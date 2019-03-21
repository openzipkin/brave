package brave.druid;


import brave.ScopedSpan;
import brave.Tracing;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import com.alibaba.druid.filter.Filter;
import com.alibaba.druid.pool.DruidDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Span;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;


public class TracingStatementFilterTest {

    static final String CREATE = " create table tbl_test (id int, name varchar(20))";

    static final String QUERY = " select * from tbl_test";

    /** JDBC is synchronous and we aren't using thread pools: everything happens on the main thread */
    ArrayList<Span> spans = new ArrayList<>();

    Tracing tracing = tracingBuilder(Sampler.ALWAYS_SAMPLE).build();
    Connection connection;

    @Before
    public void init() throws SQLException {
        DruidDataSource druidDataSource = new DruidDataSource();
        druidDataSource.setTestWhileIdle(false);
        druidDataSource.setUrl("jdbc:derby:memory:test;create=true");

        List<Filter> filters = new ArrayList<>();
        TracingStatementFilter tracingStatementFilter = new TracingStatementFilter("testServer");
        filters.add(tracingStatementFilter);

        druidDataSource.setProxyFilters(filters);

        connection = druidDataSource.getConnection();
        spans.clear();
    }

    @After
    public void close() throws SQLException {
        Tracing.current().close();
        if (connection != null) connection.close();
    }

    @Test
    public void makesChildOfCurrentSpan() throws Exception {
        ScopedSpan parent = tracing.tracer().startScopedSpan("test");
        try {

            prepareExecuteCreate(CREATE);

            prepareExecuteSelect(QUERY);
        } finally {
            parent.finish();
        }

    }

    void prepareExecuteCreate(String create) throws SQLException {
        try{
            PreparedStatement ps = connection.prepareStatement(create);
            ps.executeUpdate();
        }catch (Exception e){
        }
    }

    void prepareExecuteSelect(String query) throws SQLException {
        try{
            PreparedStatement ps = connection.prepareStatement(query);
            ResultSet resultSet = ps.executeQuery();
            while (resultSet.next()) {
                resultSet.getString(1);
            }

        }catch (Exception e){
        }
    }

    Tracing.Builder tracingBuilder(Sampler sampler) {
        return Tracing.newBuilder()
                .spanReporter(spans::add)
                .currentTraceContext(ThreadLocalCurrentTraceContext.create())
                .sampler(sampler);
    }

}