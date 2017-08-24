package brave.mysql6;

import brave.Tracer.SpanInScope;
import brave.Tracing;
import brave.propagation.StrictCurrentTraceContext;
import brave.sampler.Sampler;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentLinkedDeque;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import zipkin.Constants;
import zipkin.Span;
import zipkin.TraceKeys;
import zipkin.internal.Util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.Assume.assumeTrue;
import static zipkin.internal.Util.envOr;

public class ITTracingStatementInterceptor {
  static final String QUERY = "select 'hello world'";

  ConcurrentLinkedDeque<Span> spans = new ConcurrentLinkedDeque<>();

  Tracing tracing = tracingBuilder(Sampler.ALWAYS_SAMPLE).build();
  Connection connection;

  @Before public void init() throws SQLException {
    StringBuilder url = new StringBuilder("jdbc:mysql://");
    url.append(envOr("MYSQL_HOST", "127.0.0.1"));
    url.append(":").append(envOr("MYSQL_TCP_PORT", 3306));
    String db = envOr("MYSQL_DB", null);
    if (db != null) url.append("/").append(db);
    url.append("?statementInterceptors=").append(TracingStatementInterceptor.class.getName());
    url.append("&zipkinServiceName=").append("myservice");
    url.append("&serverTimezone=").append("UTC");

    MysqlDataSource dataSource = new MysqlDataSource();
    dataSource.setUrl(url.toString());

    dataSource.setUser(System.getenv("MYSQL_USER"));
    assumeTrue("Minimally, the environment variable MYSQL_USER must be set",
        dataSource.getUser() != null);
    dataSource.setPassword(envOr("MYSQL_PASS", ""));
    connection = dataSource.getConnection();
    spans.clear();
  }

  @After public void close() throws SQLException {
    Tracing.current().close();
    if (connection != null) connection.close();
  }

  @Test
  public void makesChildOfCurrentSpan() throws Exception {
    brave.Span parent = tracing.tracer().newTrace().name("test").start();
    try (SpanInScope ws = tracing.tracer().withSpanInScope(parent)) {
      prepareExecuteSelect(QUERY);
    } finally {
      parent.finish();
    }

    assertThat(spans)
        .hasSize(2);
  }

  @Test
  public void reportsClientAnnotationsToZipkin() throws Exception {
    prepareExecuteSelect(QUERY);

    assertThat(spans)
        .flatExtracting(s -> s.annotations)
        .extracting(a -> a.value)
        .containsExactly("cs", "cr");
  }

  @Test
  public void defaultSpanNameIsOperationName() throws Exception {
    prepareExecuteSelect(QUERY);

    assertThat(spans)
        .extracting(s -> s.name)
        .containsExactly("select");
  }

  /** This intercepts all SQL, not just queries. This ensures single-word statements work */
  @Test
  public void defaultSpanNameIsOperationName_oneWord() throws Exception {
    connection.setAutoCommit(false);
    connection.commit();

    assertThat(spans)
        .extracting(s -> s.name)
        .contains("commit");
  }

  @Test
  public void addsQueryTag() throws Exception {
    prepareExecuteSelect(QUERY);

    assertThat(spans)
        .flatExtracting(s -> s.binaryAnnotations)
        .filteredOn(a -> a.key.equals(TraceKeys.SQL_QUERY))
        .extracting(a -> new String(a.value, Util.UTF_8))
        .containsExactly(QUERY);
  }

  @Test
  public void reportsServerAddress() throws Exception {
    prepareExecuteSelect(QUERY);

    assertThat(spans)
        .flatExtracting(s -> s.binaryAnnotations)
        .extracting(b -> b.key, b -> b.endpoint.serviceName)
        .contains(tuple(Constants.SERVER_ADDR, "myservice"));
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
        .reporter(spans::add)
        .currentTraceContext(new StrictCurrentTraceContext())
        .sampler(sampler);
  }
}
