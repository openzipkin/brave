/*
 * Copyright 2018 The OpenZipkin Authors
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

package brave.mysql8;

import brave.Span;
import com.mysql.cj.jdbc.DatabaseMetaData;
import com.mysql.cj.jdbc.JdbcConnection;
import java.sql.SQLException;
import java.util.Properties;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import zipkin2.Endpoint;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TracingQueryInterceptorTest {
  @Mock
  JdbcConnection connection;
  @Mock
  DatabaseMetaData metaData;

  @Mock Span span;
  String url = "jdbc:mysql://myhost:5555/mydatabase";

  @Test public void parseServerAddress_ipFromHost_portFromUrl() throws SQLException {
    setupAndReturnPropertiesForHost("127.0.0.1");

    TracingQueryInterceptor.parseServerAddress(connection, span);

    verify(span).remoteEndpoint(Endpoint.newBuilder().serviceName("mysql")
        .ip("127.0.0.1").port(5555).build());
  }

  @Test public void parseServerAddress_serviceNameFromDatabaseName() throws SQLException {
    setupAndReturnPropertiesForHost("127.0.0.1");
    when(connection.getCatalog()).thenReturn("mydatabase");

    TracingQueryInterceptor.parseServerAddress(connection, span);

    verify(span).remoteEndpoint(Endpoint.newBuilder().serviceName("mysql-mydatabase")
        .ip("127.0.0.1").port(5555).build());
  }

  @Test public void parseServerAddress_propertiesOverrideServiceName() throws SQLException {
    setupAndReturnPropertiesForHost("127.0.0.1").setProperty("zipkinServiceName", "foo");

    TracingQueryInterceptor.parseServerAddress(connection, span);

    verify(span).remoteEndpoint(Endpoint.newBuilder().serviceName("foo")
        .ip("127.0.0.1").port(5555).build());
  }

  @Test public void parseServerAddress_emptyZipkinServiceNameIgnored() throws SQLException {
    setupAndReturnPropertiesForHost("127.0.0.1").setProperty("zipkinServiceName", "");

    TracingQueryInterceptor.parseServerAddress(connection, span);

    verify(span).remoteEndpoint(Endpoint.newBuilder().serviceName("mysql")
        .ip("127.0.0.1").port(5555).build());
  }

  @Test public void parseServerAddress_doesntNsLookup() throws SQLException {
    setupAndReturnPropertiesForHost("localhost");

    TracingQueryInterceptor.parseServerAddress(connection, span);
    verify(span).remoteEndpoint(Endpoint.newBuilder().serviceName("mysql")
        .port(5555).build());
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
