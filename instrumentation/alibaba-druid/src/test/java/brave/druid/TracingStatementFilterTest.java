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
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import zipkin2.Span;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class TracingStatementFilterTest {

    static final String QUERY = "select 'hello world'";

    /** JDBC is synchronous and we aren't using thread pools: everything happens on the main thread */
    ArrayList<Span> spans = new ArrayList<>();

    Tracing tracing = tracingBuilder(Sampler.ALWAYS_SAMPLE).build();
    Connection connection;

    @Before
    public void init() throws SQLException {
        StringBuilder url = new StringBuilder("jdbc:mysql://");
        url.append(envOr("MYSQL_HOST", "127.0.0.1"));
        url.append(":").append(envOr("MYSQL_TCP_PORT", 3306));
        String db = envOr("MYSQL_DB", null);
        if (db != null) url.append("/").append(db);

        DruidDataSource druidDataSource = new DruidDataSource();
        druidDataSource.setUrl(url.toString());
        druidDataSource.setUsername(System.getenv("MYSQL_USER"));
        druidDataSource.setPassword(envOr("MYSQL_PASS", ""));

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
            prepareExecuteSelect(QUERY);
        } finally {
            parent.finish();
        }

        assertThat(spans)
                .hasSize(2);
    }

    @Test
    public void reportsClientKindToZipkin() throws Exception {
        prepareExecuteSelect(QUERY);

        assertThat(spans)
                .extracting(Span::kind)
                .containsExactly(Span.Kind.CLIENT);
    }

    @Test
    public void defaultSpanNameIsOperationName() throws Exception {
        prepareExecuteSelect(QUERY);

        assertThat(spans)
                .extracting(Span::name)
                .containsExactly("select");
    }

    /** This intercepts all SQL, not just queries. This ensures single-word statements work */
    @Test
    public void defaultSpanNameIsOperationName_oneWord() throws Exception {
        connection.setAutoCommit(false);
        connection.commit();

        assertThat(spans)
                .extracting(Span::name)
                .contains("commit");
    }

    @Test
    public void addsQueryTag() throws Exception {
        prepareExecuteSelect(QUERY);

        assertThat(spans)
                .flatExtracting(s -> s.tags().entrySet())
                .containsExactly(entry("sql.query", QUERY));
    }

    @Test
    public void reportsServerAddress() throws Exception {
        prepareExecuteSelect(QUERY);

        assertThat(spans)
                .extracting(Span::remoteServiceName)
                .contains("myservice");
    }

    void prepareExecuteSelect(String query) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            try (ResultSet resultSet = ps.executeQuery()) {
                while (resultSet.next()) {
                    resultSet.getString(1);
                }
            }
        }
    }

    Tracing.Builder tracingBuilder(Sampler sampler) {
        return Tracing.newBuilder()
                .spanReporter(spans::add)
                .currentTraceContext(ThreadLocalCurrentTraceContext.create())
                .sampler(sampler);
    }

    static int envOr(String key, int fallback) {
        return System.getenv(key) != null ? Integer.parseInt(System.getenv(key)) : fallback;
    }

    static String envOr(String key, String fallback) {
        return System.getenv(key) != null ? System.getenv(key) : fallback;
    }

}
