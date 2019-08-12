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
package brave.p6spy;

import brave.ScopedSpan;
import brave.Span;
import brave.Tracing;
import brave.sampler.Sampler;
import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.logging.P6LogOptions;
import com.p6spy.engine.spy.option.P6OptionsRepository;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

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
  @Mock ConnectionInformation connectionInformation;

  @Mock Span span;
  String url = "jdbc:mysql://1.2.3.4:5555/mydatabase";
  String urlWithServiceName = url + "?zipkinServiceName=mysql_service&foo=bar";
  String urlWithEmptyServiceName = url + "?zipkinServiceName=&foo=bar";
  String urlWithWhiteSpace =
    "jdbc:sqlserver://1.2.3.4;databaseName=mydatabase;applicationName=Microsoft JDBC Driver for SQL Server";
  P6OptionsRepository p6OptionsRepository;
  P6LogOptions logOptions;

  @Before public void init() {
    p6OptionsRepository = new P6OptionsRepository();
    logOptions = new P6LogOptions(p6OptionsRepository);
    logOptions.load(logOptions.getDefaults());
    p6OptionsRepository.initCompleted();
  }

  @Test public void parseServerIpAndPort_IpAndPortFromUrl() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(url);

    new TracingJdbcEventListener("", false, logOptions).parseServerIpAndPort(connection, span);

    verify(span).remoteIpAndPort("1.2.3.4", 5555);
  }

  @Test public void parseServerIpAndPort_serviceNameFromDatabaseName() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(url);
    when(connection.getCatalog()).thenReturn("mydatabase");

    new TracingJdbcEventListener("", false, logOptions).parseServerIpAndPort(connection, span);

    verify(span).remoteServiceName("mydatabase");
    verify(span).remoteIpAndPort("1.2.3.4", 5555);
  }

  @Test public void parseServerIpAndPort_serviceNameFromUrl() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(urlWithServiceName);

    new TracingJdbcEventListener("", false, logOptions).parseServerIpAndPort(connection, span);

    verify(span).remoteServiceName("mysql_service");
    verify(span).remoteIpAndPort("1.2.3.4", 5555);
  }

  @Test public void parseServerIpAndPort_emptyServiceNameFromUrl() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(urlWithEmptyServiceName);
    when(connection.getCatalog()).thenReturn("mydatabase");

    new TracingJdbcEventListener("", false, logOptions).parseServerIpAndPort(connection, span);

    verify(span).remoteServiceName("mydatabase");
    verify(span).remoteIpAndPort("1.2.3.4", 5555);
  }

  @Test public void parseServerIpAndPort_overrideServiceName() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(url);

    new TracingJdbcEventListener("foo", false, logOptions).parseServerIpAndPort(connection, span);

    verify(span).remoteServiceName("foo");
    verify(span).remoteIpAndPort("1.2.3.4", 5555);
  }

  @Test public void parseServerIpAndPort_doesntCrash() throws SQLException {
    when(connection.getMetaData()).thenThrow(new SQLException());

    new TracingJdbcEventListener("", false, logOptions).parseServerIpAndPort(connection, span);

    verifyNoMoreInteractions(span);
  }

  @Test public void parseServerIpAndPort_withWhiteSpace() throws SQLException {
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getURL()).thenReturn(urlWithWhiteSpace);

    new TracingJdbcEventListener("foo", false, logOptions).parseServerIpAndPort(connection, span);

    verify(span).remoteServiceName("foo");
  }

  @Test public void shouldFilterSqlExclusion() throws SQLException {
    ArrayList<zipkin2.Span> spans = new ArrayList<>();
    try (Tracing tracing = tracingBuilder(Sampler.ALWAYS_SAMPLE, spans).build()) {
      logOptions.setFilter(true);
      logOptions.setExclude("set session");
      when(statementInformation.getSql()).thenReturn("set session foo foo;");
      when(statementInformation.getConnectionInformation()).thenReturn(connectionInformation);
      when(connectionInformation.getConnection()).thenReturn(connection);
      when(connection.getMetaData()).thenReturn(metaData);

      TracingJdbcEventListener listener = new TracingJdbcEventListener("", false, logOptions);
      listener.onBeforeAnyExecute(statementInformation);
      listener.onAfterAnyExecute(statementInformation, 1, null);

      logOptions.setFilter(false);
      listener.onBeforeAnyExecute(statementInformation);
      listener.onAfterAnyExecute(statementInformation, 1, null);

      assertThat(spans).size().isEqualTo(1);
    }
  }

  @Test public void nullSqlWontNPE() throws SQLException {
    ArrayList<zipkin2.Span> spans = new ArrayList<>();
    try (Tracing tracing = tracingBuilder(Sampler.ALWAYS_SAMPLE, spans).build()) {

      when(statementInformation.getSql()).thenReturn(null);

      TracingJdbcEventListener listener = new TracingJdbcEventListener("", false, logOptions);
      listener.onBeforeAnyExecute(statementInformation);
      listener.onAfterAnyExecute(statementInformation, 1, null);

      assertThat(spans).isEmpty();
    }
  }

  @Test public void handleAfterExecute_without_beforeExecute_getting_called() {
    Tracing tracing = tracingBuilder(Sampler.ALWAYS_SAMPLE, new ArrayList<>()).build();
    ScopedSpan parent = tracing.tracer().startScopedSpan("test");
    try {
      TracingJdbcEventListener listener = new TracingJdbcEventListener("", false, logOptions);
      listener.onAfterAnyExecute(statementInformation, 1, null);
      listener.onAfterAnyExecute(statementInformation, 1, null);
    } finally {
      parent.finish();
      tracing.close();
    }
  }
}
