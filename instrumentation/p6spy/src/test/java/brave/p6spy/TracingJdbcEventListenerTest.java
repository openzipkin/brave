package brave.p6spy;

import brave.Span;
import brave.Tracing;
import brave.sampler.Sampler;
import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.common.StatementInformation;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import zipkin.Endpoint;

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

  @Test public void parseServerAddress_ipAndPortFromUrl() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(url);

    new TracingJdbcEventListener("", false).parseServerAddress(connection, span);

    verify(span).remoteEndpoint(Endpoint.builder().serviceName("")
        .ipv4(127 << 24 | 1).port(5555).build());
  }

  @Test public void parseServerAddress_serviceNameFromDatabaseName() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(url);
    when(connection.getCatalog()).thenReturn("mydatabase");

    new TracingJdbcEventListener("", false).parseServerAddress(connection, span);

    verify(span).remoteEndpoint(Endpoint.builder().serviceName("mydatabase")
        .ipv4(127 << 24 | 1).port(5555).build());
  }

  @Test public void parseServerAddress_serviceNameFromUrl() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(urlWithServiceName);
    when(connection.getCatalog()).thenReturn("mydatabase");

    new TracingJdbcEventListener("", false).parseServerAddress(connection, span);

    verify(span).remoteEndpoint(Endpoint.builder().serviceName("mysql_service")
        .ipv4(127 << 24 | 1).port(5555).build());
  }

  @Test public void parseServerAddress_emptyServiceNameFromUrl() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(urlWithEmptyServiceName);
    when(connection.getCatalog()).thenReturn("mydatabase");

    new TracingJdbcEventListener("", false).parseServerAddress(connection, span);

    verify(span).remoteEndpoint(Endpoint.builder().serviceName("mydatabase")
        .ipv4(127 << 24 | 1).port(5555).build());
  }

  @Test public void parseServerAddress_overrideServiceName() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(url);

    new TracingJdbcEventListener("foo", false).parseServerAddress(connection, span);

    verify(span).remoteEndpoint(Endpoint.builder().serviceName("foo")
        .ipv4(127 << 24 | 1).port(5555).build());
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


  @Test public void nullSqlWontNPE() throws SQLException {
    ConcurrentLinkedDeque<zipkin.Span> spans = new ConcurrentLinkedDeque<>();
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
}
