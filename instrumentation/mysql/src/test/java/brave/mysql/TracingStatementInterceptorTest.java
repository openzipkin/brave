/*
 * Copyright 2013-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.mysql;

import brave.Span;
import com.mysql.jdbc.Connection;
import com.mysql.jdbc.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Properties;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TracingStatementInterceptorTest {
  @Mock Connection connection;
  @Mock DatabaseMetaData metaData;

  @Mock Span span;
  String url = "jdbc:mysql://myhost:5555/mydatabase";

  @Test public void parseServerIpAndPort_ipFromHost_portFromUrl() throws SQLException {
    setupAndReturnPropertiesForHost("1.2.3.4");

    TracingStatementInterceptor.parseServerIpAndPort(connection, span);

    verify(span).remoteServiceName("mysql");
    verify(span).remoteIpAndPort("1.2.3.4", 5555);
  }

  @Test public void parseServerIpAndPort_serviceNameFromDatabaseName() throws SQLException {
    setupAndReturnPropertiesForHost("1.2.3.4");
    when(connection.getCatalog()).thenReturn("mydatabase");

    TracingStatementInterceptor.parseServerIpAndPort(connection, span);

    verify(span).remoteServiceName("mysql-mydatabase");
    verify(span).remoteIpAndPort("1.2.3.4", 5555);
  }

  @Test public void parseServerIpAndPort_propertiesOverrideServiceName() throws SQLException {
    setupAndReturnPropertiesForHost("1.2.3.4").setProperty("zipkinServiceName", "foo");

    TracingStatementInterceptor.parseServerIpAndPort(connection, span);

    verify(span).remoteServiceName("foo");
    verify(span).remoteIpAndPort("1.2.3.4", 5555);
  }

  @Test public void parseServerIpAndPort_emptyZipkinServiceNameIgnored() throws SQLException {
    setupAndReturnPropertiesForHost("1.2.3.4").setProperty("zipkinServiceName", "");

    TracingStatementInterceptor.parseServerIpAndPort(connection, span);

    verify(span).remoteServiceName("mysql");
    verify(span).remoteIpAndPort("1.2.3.4", 5555);
  }

  @Test public void parseServerIpAndPort_doesntCrash() throws SQLException {
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
