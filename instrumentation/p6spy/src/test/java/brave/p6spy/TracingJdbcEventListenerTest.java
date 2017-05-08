package brave.p6spy;

import brave.Span;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import zipkin.Endpoint;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TracingJdbcEventListenerTest {
  @Mock Connection connection;
  @Mock DatabaseMetaData metaData;

  @Mock Span span;
  String url = "jdbc:mysql://127.0.0.1:5555/mydatabase";

  @Test public void parseServerAddress_ipAndPortFromUrl() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(url);

    new TracingJdbcEventListener("").parseServerAddress(connection, span);

    verify(span).remoteEndpoint(Endpoint.builder().serviceName("")
        .ipv4(127 << 24 | 1).port(5555).build());
  }

  @Test public void parseServerAddress_serviceNameFromDatabaseName() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(url);
    when(connection.getCatalog()).thenReturn("mydatabase");

    new TracingJdbcEventListener("").parseServerAddress(connection, span);

    verify(span).remoteEndpoint(Endpoint.builder().serviceName("mydatabase")
        .ipv4(127 << 24 | 1).port(5555).build());
  }

  @Test public void parseServerAddress_overrideServiceName() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(url);

    new TracingJdbcEventListener("foo").parseServerAddress(connection, span);

    verify(span).remoteEndpoint(Endpoint.builder().serviceName("foo")
        .ipv4(127 << 24 | 1).port(5555).build());
  }

  @Test public void parseServerAddress_doesntNsLookup() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn("jdbc:mysql://localhost:5555/mydatabase");

    new TracingJdbcEventListener("").parseServerAddress(connection, span);
    verifyNoMoreInteractions(span);
  }

  @Test public void parseServerAddress_doesntCrash() throws SQLException {
    when(connection.getMetaData()).thenThrow(new SQLException());

    verifyNoMoreInteractions(span);
  }
}
