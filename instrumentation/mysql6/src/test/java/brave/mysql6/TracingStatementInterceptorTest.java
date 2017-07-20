package brave.mysql6;

import brave.Span;

import java.sql.SQLException;
import java.util.Properties;

import com.mysql.cj.api.jdbc.JdbcConnection;
import com.mysql.cj.jdbc.DatabaseMetaData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import zipkin.Endpoint;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TracingStatementInterceptorTest {
  @Mock
  JdbcConnection connection;
  @Mock
  DatabaseMetaData metaData;

  @Mock Span span;
  String url = "jdbc:mysql://myhost:5555/mydatabase";

  @Test public void parseServerAddress_ipFromHost_portFromUrl() throws SQLException {
    setupAndReturnPropertiesForHost("127.0.0.1");

    TracingStatementInterceptor.parseServerAddress(connection, span);

    verify(span).remoteEndpoint(Endpoint.builder().serviceName("mysql")
        .ipv4(127 << 24 | 1).port(5555).build());
  }

  @Test public void parseServerAddress_serviceNameFromDatabaseName() throws SQLException {
    setupAndReturnPropertiesForHost("127.0.0.1");
    when(connection.getCatalog()).thenReturn("mydatabase");

    TracingStatementInterceptor.parseServerAddress(connection, span);

    verify(span).remoteEndpoint(Endpoint.builder().serviceName("mysql-mydatabase")
        .ipv4(127 << 24 | 1).port(5555).build());
  }

  @Test public void parseServerAddress_propertiesOverrideServiceName() throws SQLException {
    setupAndReturnPropertiesForHost("127.0.0.1").setProperty("zipkinServiceName", "foo");

    TracingStatementInterceptor.parseServerAddress(connection, span);

    verify(span).remoteEndpoint(Endpoint.builder().serviceName("foo")
        .ipv4(127 << 24 | 1).port(5555).build());
  }

  @Test public void parseServerAddress_emptyZipkinServiceNameIgnored() throws SQLException {
    setupAndReturnPropertiesForHost("127.0.0.1").setProperty("zipkinServiceName", "");

    TracingStatementInterceptor.parseServerAddress(connection, span);

    verify(span).remoteEndpoint(Endpoint.builder().serviceName("mysql")
        .ipv4(127 << 24 | 1).port(5555).build());
  }

  @Test public void parseServerAddress_doesntNsLookup() throws SQLException {
    setupAndReturnPropertiesForHost("localhost");

    TracingStatementInterceptor.parseServerAddress(connection, span);
    verifyNoMoreInteractions(span);
  }

  @Test public void parseServerAddress_doesntCrash() throws SQLException {
    when(connection.getMetaData()).thenThrow(new SQLException());

    verifyNoMoreInteractions(span);
  }

  Properties setupAndReturnPropertiesForHost(String host) throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(connection.getURL()).thenReturn(url);
    Properties properties = new Properties();
    when(connection.getProperties()).thenReturn(properties);
    when(connection.getHost()).thenReturn(host);
    return properties;
  }
}
