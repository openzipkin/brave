/*
 * Copyright 2013-2024 The OpenZipkin Authors
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
package brave.p6spy;

import brave.ScopedSpan;
import brave.Span;
import brave.Tracing;
import brave.propagation.StrictCurrentTraceContext;
import brave.test.TestSpanHandler;
import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.logging.P6LogOptions;
import com.p6spy.engine.spy.option.P6OptionsRepository;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TracingJdbcEventListenerTest {
  @Mock Connection connection;
  @Mock DatabaseMetaData metaData;
  @Mock StatementInformation statementInformation;
  @Mock ConnectionInformation connectionInformation;

  @Mock Span span;
  String url = "jdbc:mysql://1.2.3.4:5555/mydatabase";
  String urlWithServiceName = url + "?zipkinServiceName=mysql_service&foo=bar";
  String urlWithEmptyServiceName = url + "?zipkinServiceName=&foo=bar";
  String urlWithWhiteSpace =
    "jdbc:sqlserver://1.2.3.4;databaseName=mydatabase;applicationName=Microsoft JDBC Driver for SQL Server";
  P6OptionsRepository p6OptionsRepository;
  P6LogOptions logOptions;

  StrictCurrentTraceContext currentTraceContext = StrictCurrentTraceContext.create();
  TestSpanHandler spans = new TestSpanHandler();
  Tracing tracing = Tracing.newBuilder()
    .currentTraceContext(currentTraceContext).addSpanHandler(spans).build();

  @BeforeEach public void init() {
    p6OptionsRepository = new P6OptionsRepository();
    logOptions = new P6LogOptions(p6OptionsRepository);
    logOptions.load(logOptions.getDefaults());
    p6OptionsRepository.initCompleted();
  }

  @AfterEach public void close() {
    tracing.close();
    currentTraceContext.close();
  }

  @Test void parseServerIpAndPort_IpAndPortFromUrl() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(url);

    new TracingJdbcEventListener("", false, false, logOptions).parseServerIpAndPort(connection, span);

    verify(span).remoteIpAndPort("1.2.3.4", 5555);
  }

  @Test void parseServerIpAndPort_serviceNameFromDatabaseName() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(url);
    when(connection.getCatalog()).thenReturn("mydatabase");

    new TracingJdbcEventListener("", false, false, logOptions).parseServerIpAndPort(connection, span);

    verify(span).remoteServiceName("mydatabase");
    verify(span).remoteIpAndPort("1.2.3.4", 5555);
  }

  @Test void parseServerIpAndPort_serviceNameFromUrl() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(urlWithServiceName);

    new TracingJdbcEventListener("", false, false, logOptions).parseServerIpAndPort(connection, span);

    verify(span).remoteServiceName("mysql_service");
    verify(span).remoteIpAndPort("1.2.3.4", 5555);
  }

  @Test void parseServerIpAndPort_emptyServiceNameFromUrl() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(urlWithEmptyServiceName);
    when(connection.getCatalog()).thenReturn("mydatabase");

    new TracingJdbcEventListener("", false, false, logOptions).parseServerIpAndPort(connection, span);

    verify(span).remoteServiceName("mydatabase");
    verify(span).remoteIpAndPort("1.2.3.4", 5555);
  }

  @Test void parseServerIpAndPort_overrideServiceName() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(url);

    new TracingJdbcEventListener("foo", false, false, logOptions).parseServerIpAndPort(connection, span);

    verify(span).remoteServiceName("foo");
    verify(span).remoteIpAndPort("1.2.3.4", 5555);
  }

  static Object[][] exceptionsTraced() {
    return new Object[][] {
      {"?zipkinServiceName=myDatabase&foo=bar", "myDatabase"},
      {"?zipkinServiceName=my_database&foo=bar", "my_database"},
      {"?zipkinServiceName=my-database&foo=bar", "my-database"},
      {"?zipkinServiceName=my-database-1&foo=bar", "my-database-1"},
      {"?zipkinServiceName=my.database&foo=bar", "my.database"},
      {"?zipkinServiceName=my-database:5432&foo=bar", "my-database:5432"},
      {"?zipkinServiceName=my-database@localhost&foo=bar", "my-database@localhost"},
      {"?zipkinServiceName=my-database", "my-database"},
      {"?zipkinServiceName=my-database-1&zipkinServiceName=my-database-2", "my-database-1"},
      {"?zipkinServiceName=", null},
      {"?zipkinServiceName=&", null},
      {"", null}
    };
  }

  @ExtendWith(MockitoExtension.class)
  @Nested
  class ParserTest {
    @Mock Connection connection;
    @Mock DatabaseMetaData metaData;

    @Mock Span span;
    String queryString;
    String remoteServiceName;

    @MethodSource("brave.p6spy.TracingJdbcEventListenerTest#exceptionsTraced")
    @ParameterizedTest(name = "remoteServiceName for {0} should be '{1}'")
    void parseServerIpAndPort_overridesRemoteServiceNameFromUrlParameter(String queryString, String remoteServiceName)
      throws SQLException {
      initParserTest(queryString, remoteServiceName);
      when(connection.getMetaData()).thenReturn(metaData);
      when(metaData.getURL()).thenReturn("jdbc:mysql://1.2.3.4:5555/mydatabase" + queryString);

      new TracingJdbcEventListener(null, false, false,
        P6LogOptions.getActiveInstance()).parseServerIpAndPort(connection, span);

      if (remoteServiceName != null) { // shouldn't invoke if no service name was parsed
        verify(span).remoteServiceName(remoteServiceName);
      }
      verify(span).remoteIpAndPort("1.2.3.4", 5555);
    }

    public void initParserTest(String queryString, String remoteServiceName) {
      this.queryString = queryString;
      this.remoteServiceName = remoteServiceName;
    }
  }

  @Test void parseServerIpAndPort_doesntCrash() throws SQLException {
    when(connection.getMetaData()).thenThrow(new SQLException());

    new TracingJdbcEventListener("", false, false, logOptions).parseServerIpAndPort(connection, span);

    verifyNoMoreInteractions(span);
  }

  @Test void parseServerIpAndPort_withWhiteSpace() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(urlWithWhiteSpace);

    new TracingJdbcEventListener("foo", false, false, logOptions).parseServerIpAndPort(connection, span);

    verify(span).remoteServiceName("foo");
  }

  @Test void shouldFilterSqlExclusion() throws SQLException {
    logOptions.setFilter(true);
    logOptions.setExclude("set session");
    when(statementInformation.getSql()).thenReturn("set session foo foo;");
    when(statementInformation.getConnectionInformation()).thenReturn(connectionInformation);
    when(connectionInformation.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(metaData);

    TracingJdbcEventListener listener = new TracingJdbcEventListener("", false, false, logOptions);
    listener.onBeforeAnyExecute(statementInformation);
    listener.onAfterAnyExecute(statementInformation, 1, null);

    logOptions.setFilter(false);
    listener.onBeforeAnyExecute(statementInformation);
    listener.onAfterAnyExecute(statementInformation, 1, null);

    assertThat(spans).size().isEqualTo(1);
  }

  @Test void nullSqlWontNPE() {
    when(statementInformation.getSql()).thenReturn(null);

    TracingJdbcEventListener listener = new TracingJdbcEventListener("", false, false, logOptions);
    listener.onBeforeAnyExecute(statementInformation);
    listener.onAfterAnyExecute(statementInformation, 1, null);

    assertThat(spans).isEmpty();
  }

  @Test void handleAfterExecute_without_beforeExecute_getting_called() {
    ScopedSpan parent = tracing.tracer().startScopedSpan("test");
    try {
      TracingJdbcEventListener listener = new TracingJdbcEventListener("", false, false, logOptions);
      listener.onAfterAnyExecute(statementInformation, 1, null);
      listener.onAfterAnyExecute(statementInformation, 1, null);
    } finally {
      parent.finish();
    }
  }
}
