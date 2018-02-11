package brave.p6spy;

import brave.Span;
import brave.Tracer.SpanInScope;
import brave.Tracing;
import brave.sampler.Sampler;
import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.common.StatementInformation;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import zipkin2.Endpoint;

import static brave.p6spy.ITTracingP6Factory.tracingBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TracingJdbcEventListenerTest {
  @Mock Connection connection;
  @Mock DatabaseMetaData metaData;
  @Mock StatementInformation statementInformation;
  @Mock ConnectionInformation ci;

  @Mock Span span;
  String url = "jdbc:mysql://127.0.0.1:5555/mydatabase";
  String urlWithServiceName = url + "?zipkinServiceName=mysql_service&foo=bar";
  String urlWithEmptyServiceName = url + "?zipkinServiceName=&foo=bar";
  String urlWithWhiteSpace =
      "jdbc:sqlserver://127.0.0.1;databaseName=mydatabase;applicationName=Microsoft JDBC Driver for SQL Server";


  @Test public void parseServerAddress_ipAndPortFromUrl() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(url);

    new TracingJdbcEventListener("", false).parseServerAddress(connection, span);

    verify(span).remoteEndpoint(Endpoint.newBuilder().ip("127.0.0.1").port(5555).build());
  }

  @Test public void parseServerAddress_serviceNameFromDatabaseName() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(url);
    when(connection.getCatalog()).thenReturn("mydatabase");

    new TracingJdbcEventListener("", false).parseServerAddress(connection, span);

    verify(span).remoteEndpoint(
        Endpoint.newBuilder().serviceName("mydatabase").ip("127.0.0.1").port(5555).build()
    );
  }

  @Test public void parseServerAddress_serviceNameFromUrl() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(urlWithServiceName);
    when(connection.getCatalog()).thenReturn("mydatabase");

    new TracingJdbcEventListener("", false).parseServerAddress(connection, span);

    verify(span).remoteEndpoint(
        Endpoint.newBuilder().serviceName("mysql_service").ip("127.0.0.1").port(5555).build()
    );
  }

  @Test public void parseServerAddress_emptyServiceNameFromUrl() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(urlWithEmptyServiceName);
    when(connection.getCatalog()).thenReturn("mydatabase");

    new TracingJdbcEventListener("", false).parseServerAddress(connection, span);

    verify(span).remoteEndpoint(
        Endpoint.newBuilder().serviceName("mydatabase").ip("127.0.0.1").port(5555).build()
    );
  }

  @Test public void parseServerAddress_overrideServiceName() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(url);

    new TracingJdbcEventListener("foo", false).parseServerAddress(connection, span);

    verify(span).remoteEndpoint(
        Endpoint.newBuilder().serviceName("foo").ip("127.0.0.1").port(5555).build()
    );
  }

  @Test public void parseServerAddress_doesntNsLookup() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn("jdbc:mysql://localhost:5555/mydatabase");

    new TracingJdbcEventListener("", false).parseServerAddress(connection, span);
    verifyNoMoreInteractions(span);
  }

  @Test public void parseServerAddress_doesntCrash() throws SQLException {
    when(connection.getMetaData()).thenThrow(new SQLException());

    verifyNoMoreInteractions(span);
  }

  @Test public void parseServerAddress_withWhiteSpace() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(urlWithWhiteSpace);

    new TracingJdbcEventListener("foo", false).parseServerAddress(connection, span);

    verify(span).remoteEndpoint(
        Endpoint.newBuilder().serviceName("foo").build()
    );
  }

  @Test public void nullSqlWontNPE() throws SQLException {
    ArrayList<zipkin2.Span> spans = new ArrayList<>();
    try (Tracing tracing = tracingBuilder(Sampler.ALWAYS_SAMPLE, spans).build()) {

      when(statementInformation.getSql()).thenReturn(null);
      when(statementInformation.getConnectionInformation()).thenReturn(ci);
      when(ci.getConnection()).thenReturn(connection);
      when(connection.getMetaData()).thenReturn(metaData);
      when(metaData.getURL()).thenReturn(url);

      TracingJdbcEventListener listener = new TracingJdbcEventListener("", false);
      listener.onBeforeAnyExecute(statementInformation);
      listener.onAfterAnyExecute(statementInformation, 1, null);

      assertThat(spans).isEmpty();
    }
  }

  @Test public void handleAfterExecute_without_beforeExecute_getting_called() {
    Tracing tracing = tracingBuilder(Sampler.ALWAYS_SAMPLE, new ArrayList<>()).build();
    Span span = tracing.tracer().nextSpan().start();
    try (SpanInScope spanInScope = tracing.tracer().withSpanInScope(span)) {
      TracingJdbcEventListener listener = new TracingJdbcEventListener("", false);
      listener.onAfterAnyExecute(statementInformation, 1, null);
      listener.onAfterAnyExecute(statementInformation, 1, null);
    }
  }
}
