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
package brave.mysql6;

import brave.ScopedSpan;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.propagation.StrictCurrentTraceContext;
import brave.test.TestSpanHandler;
import com.mysql.cj.jdbc.MysqlDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static brave.Span.Kind.CLIENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.Assume.assumeTrue;

@Ignore("WrongArgumentException: Unable to load authentication plugin 'caching_sha2_password'")
public class ITTracingStatementInterceptor {
  static final String QUERY = "select 'hello world'";

  StrictCurrentTraceContext currentTraceContext = StrictCurrentTraceContext.create();
  TestSpanHandler spans = new TestSpanHandler();
  Tracing tracing = Tracing.newBuilder()
      .currentTraceContext(currentTraceContext).addSpanHandler(spans).build();

  Connection connection;

  @Before public void init() throws SQLException {
    StringBuilder url = new StringBuilder("jdbc:mysql://");
    url.append(envOr("MYSQL_HOST", "127.0.0.1"));
    url.append(":").append(envOr("MYSQL_TCP_PORT", 3306));
    String db = envOr("MYSQL_DB", null);
    if (db != null) url.append("/").append(db);
    url.append("?statementInterceptors=").append(TracingStatementInterceptor.class.getName());
    url.append("&zipkinServiceName=").append("myservice");
    url.append("&serverTimezone=").append("UTC");

    MysqlDataSource dataSource = new MysqlDataSource();
    dataSource.setUrl(url.toString());

    dataSource.setUser(System.getenv("MYSQL_USER"));
    assumeTrue("Minimally, the environment variable MYSQL_USER must be set",
      dataSource.getUser() != null);
    dataSource.setPassword(envOr("MYSQL_PASS", ""));
    connection = dataSource.getConnection();
    spans.clear();
  }

  @After public void close() throws SQLException {
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
      .containsExactly(CLIENT);
  }

  @Test
  public void defaultSpanNameIsOperationName() throws Exception {
    prepareExecuteSelect(QUERY);

    assertThat(spans)
      .extracting(MutableSpan::name)
      .containsExactly("select");
  }

  /** This intercepts all SQL, not just queries. This ensures single-word statements work */
  @Test
  public void defaultSpanNameIsOperationName_oneWord() throws Exception {
    connection.setAutoCommit(false);
    connection.commit();

    assertThat(spans)
      .extracting(MutableSpan::name)
      .contains("commit");
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
      .contains("myservice");
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

  static int envOr(String key, int fallback) {
    return System.getenv(key) != null ? Integer.parseInt(System.getenv(key)) : fallback;
  }

  static String envOr(String key, String fallback) {
    return System.getenv(key) != null ? System.getenv(key) : fallback;
  }
}
