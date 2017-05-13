package brave.p6spy;

import brave.Tracer.SpanInScope;
import brave.Tracing;
import brave.internal.StrictCurrentTraceContext;
import brave.sampler.Sampler;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import zipkin.Constants;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.TraceKeys;
import zipkin.internal.Util;
import zipkin.storage.InMemoryStorage;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class ITTracingP6Factory {
  static final String URL = "jdbc:p6spy:derby:memory:p6spy;create=true";
  static final String QUERY = "SELECT 1 FROM SYSIBM.SYSDUMMY1";

  //Get rid of annoying derby.log
  static {
    DerbyUtils.disableLog();
  }

  Endpoint local = Endpoint.builder().serviceName("local").ipv4(127 << 24 | 1).port(100).build();
  InMemoryStorage storage = new InMemoryStorage();

  Tracing tracing = tracingBuilder(Sampler.ALWAYS_SAMPLE).build();
  Connection connection;

  @Before
  public void setup() throws Exception {
    DriverManager.getDriver(URL);
    connection = DriverManager.getConnection(URL, "foo", "bar");
  }

  @After
  public void close() throws Exception {
    Tracing.current().close();
    connection.close();
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
        .reporter(s -> storage.spanConsumer().accept(asList(s)))
        .currentTraceContext(new StrictCurrentTraceContext())
        .localEndpoint(local)
        .sampler(sampler);
  }

  List<Span> spans {
    List<List<Span>> result = storage.spanStore().getRawTraces();
    assertThat(result).hasSize(1);
    return result.get(0);
  }
}
