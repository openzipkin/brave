/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
import brave.Span.Kind;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.propagation.StrictCurrentTraceContext;
import brave.test.TestSpanHandler;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class ITTracingP6Factory {
  static final String URL = "jdbc:p6spy:derby:memory:p6spy;create=true";
  static final String QUERY = "SELECT 1 FROM SYSIBM.SYSDUMMY1";

  //Get rid of annoying derby.log
  static {
    DerbyUtils.disableLog();
  }

  /** JDBC is synchronous and we aren't using thread pools: everything happens on the main thread */
  StrictCurrentTraceContext currentTraceContext = StrictCurrentTraceContext.create();
  TestSpanHandler spans = new TestSpanHandler();
  Tracing tracing = Tracing.newBuilder()
    .currentTraceContext(currentTraceContext).addSpanHandler(spans).build();
  Connection connection;

  @Before
  public void setup() throws Exception {
    DriverManager.getDriver(URL);
    connection = DriverManager.getConnection(URL, "foo", "bar");
  }

  @After
  public void close() throws Exception {
    if (connection != null) connection.close();
    tracing.close();
    currentTraceContext.close();
  }

  @Test
  public void makesChildOfCurrentSpan() throws Exception {
    ScopedSpan parent = tracing.tracer().startScopedSpan("test");
    try {
      prepareExecuteSelect(QUERY);
    } finally {
      parent.finish();
    }

    assertThat(spans)
      .hasSize(2);
  }

  @Test
  public void reportsClientKindToZipkin() throws Exception {
    prepareExecuteSelect(QUERY);

    assertThat(spans)
      .extracting(MutableSpan::kind)
      .containsExactly(Kind.CLIENT);
  }

  @Test
  public void defaultSpanNameIsOperationName() throws Exception {
    prepareExecuteSelect(QUERY);

    assertThat(spans)
      .extracting(MutableSpan::name)
      .containsExactly("SELECT");
  }

  @Test
  public void addsQueryTag() throws Exception {
    prepareExecuteSelect(QUERY);

    assertThat(spans)
      .flatExtracting(s -> s.tags().entrySet())
      .containsExactly(entry("sql.query", QUERY));
  }

  @Test
  public void reportsServerAddress() throws Exception {
    prepareExecuteSelect(QUERY);

    assertThat(spans)
      .extracting(MutableSpan::remoteServiceName)
      .containsExactly("myservice");
  }

  void prepareExecuteSelect(String query) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(query)) {
      try (ResultSet resultSet = ps.executeQuery()) {
        while (resultSet.next()) {
          resultSet.getString(1);
        }
      }
    }
  }
}
