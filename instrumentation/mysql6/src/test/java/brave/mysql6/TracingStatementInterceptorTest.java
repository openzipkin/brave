package brave.mysql6;

import brave.Span;
import com.mysql.cj.api.jdbc.JdbcConnection;
import java.sql.SQLException;
import java.util.Properties;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import zipkin2.Endpoint;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TracingStatementInterceptorTest {
  @Mock JdbcConnection connection;

  @Mock Span span;
  String url = "jdbc:mysql://myhost:5555/mydatabase";

  @Test public void parseServerAddress_ipFromHost_portFromUrl() throws SQLException {
    setupAndReturnPropertiesForHost("127.0.0.1");

    TracingStatementInterceptor.parseServerAddress(connection, span);

    verify(span).remoteEndpoint(Endpoint.newBuilder().serviceName("mysql")
        .ip("127.0.0.1").port(5555).build());
  }

  @Test public void parseServerAddress_serviceNameFromDatabaseName() throws SQLException {
    setupAndReturnPropertiesForHost("127.0.0.1");
    when(connection.getCatalog()).thenReturn("mydatabase");

    TracingStatementInterceptor.parseServerAddress(connection, span);

    verify(span).remoteEndpoint(Endpoint.newBuilder().serviceName("mysql-mydatabase")
        .ip("127.0.0.1").port(5555).build());
  }

  @Test public void parseServerAddress_propertiesOverrideServiceName() throws SQLException {
    setupAndReturnPropertiesForHost("127.0.0.1").setProperty("zipkinServiceName", "foo");

    TracingStatementInterceptor.parseServerAddress(connection, span);

    verify(span).remoteEndpoint(Endpoint.newBuilder().serviceName("foo")
        .ip("127.0.0.1").port(5555).build());
  }

  @Test public void parseServerAddress_emptyZipkinServiceNameIgnored() throws SQLException {
    setupAndReturnPropertiesForHost("127.0.0.1").setProperty("zipkinServiceName", "");

    TracingStatementInterceptor.parseServerAddress(connection, span);

    verify(span).remoteEndpoint(Endpoint.newBuilder().serviceName("mysql")
        .ip("127.0.0.1").port(5555).build());
  }

  @Test public void parseServerAddress_doesntNsLookup() throws SQLException {
    setupAndReturnPropertiesForHost("localhost");

    TracingStatementInterceptor.parseServerAddress(connection, span);
    verify(span).remoteEndpoint(Endpoint.newBuilder().serviceName("mysql")
        .port(5555).build());
  }

  @Test public void parseServerAddress_doesntCrash() {
    when(connection.getURL()).thenThrow(new RuntimeException());

    TracingStatementInterceptor.parseServerAddress(connection, span);

    verifyNoMoreInteractions(span);
  }

  Properties setupAndReturnPropertiesForHost(String host) {
    when(connection.getURL()).thenReturn(url);
    Properties properties = new Properties();
    when(connection.getProperties()).thenReturn(properties);
    when(connection.getHost()).thenReturn(host);
    return properties;
  }
}
