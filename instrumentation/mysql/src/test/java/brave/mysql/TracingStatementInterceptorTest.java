/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.mysql;

import brave.Span;
import com.mysql.jdbc.Connection;
import com.mysql.jdbc.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TracingStatementInterceptorTest {
  @Mock Connection connection;
  @Mock DatabaseMetaData metaData;

  @Mock Span span;
  String url = "jdbc:mysql://myhost:5555/mydatabase";

  @Test void parseServerIpAndPort_ipFromHost_portFromUrl() throws SQLException {
    setupAndReturnPropertiesForHost("1.2.3.4");

    TracingStatementInterceptor.parseServerIpAndPort(connection, span);

    verify(span).remoteServiceName("mysql");
    verify(span).remoteIpAndPort("1.2.3.4", 5555);
  }

  @Test void parseServerIpAndPort_serviceNameFromDatabaseName() throws SQLException {
    setupAndReturnPropertiesForHost("1.2.3.4");
    when(connection.getCatalog()).thenReturn("mydatabase");

    TracingStatementInterceptor.parseServerIpAndPort(connection, span);

    verify(span).remoteServiceName("mysql-mydatabase");
    verify(span).remoteIpAndPort("1.2.3.4", 5555);
  }

  @Test void parseServerIpAndPort_propertiesOverrideServiceName() throws SQLException {
    setupAndReturnPropertiesForHost("1.2.3.4").setProperty("zipkinServiceName", "foo");

    TracingStatementInterceptor.parseServerIpAndPort(connection, span);

    verify(span).remoteServiceName("foo");
    verify(span).remoteIpAndPort("1.2.3.4", 5555);
  }

  @Test void parseServerIpAndPort_emptyZipkinServiceNameIgnored() throws SQLException {
    setupAndReturnPropertiesForHost("1.2.3.4").setProperty("zipkinServiceName", "");

    TracingStatementInterceptor.parseServerIpAndPort(connection, span);

    verify(span).remoteServiceName("mysql");
    verify(span).remoteIpAndPort("1.2.3.4", 5555);
  }

  @Test void parseServerIpAndPort_doesntCrash() throws SQLException {
    when(connection.getMetaData()).thenThrow(new SQLException());

    TracingStatementInterceptor.parseServerIpAndPort(connection, span);

    verifyNoMoreInteractions(span);
  }

  Properties setupAndReturnPropertiesForHost(String host) throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(url);
    Properties properties = new Properties();
    when(connection.getProperties()).thenReturn(properties);
    when(connection.getHost()).thenReturn(host);
    return properties;
  }
}
