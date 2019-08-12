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
package brave.mysql8;

import brave.ScopedSpan;
import brave.Tracing;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import com.mysql.cj.jdbc.MysqlDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class ITTracingQueryInterceptor {
  static final String QUERY = "select 'hello world'";
  static final String ERROR_QUERY = "select unknown_field FROM unknown_table";

  @Parameterized.Parameters(name = "exceptions traced: {0}")
  public static Iterable<Boolean> exceptionsTraced() {
    return Arrays.asList(false, true);
  }

  @Parameterized.Parameter
  public boolean exceptionsTraced;

  /** JDBC is synchronous and we aren't using thread pools: everything happens on the main thread */
  ArrayList<Span> spans = new ArrayList<>();

  Tracing tracing = tracingBuilder(Sampler.ALWAYS_SAMPLE).build();
  Connection connection;

  @Before public void init() throws SQLException {
    StringBuilder url = new StringBuilder("jdbc:mysql://");
    url.append(envOr("MYSQL_HOST", "127.0.0.1"));
    url.append(":").append(envOr("MYSQL_TCP_PORT", 3306));
    String db = envOr("MYSQL_DB", null);
    if (db != null) url.append("/").append(db);
    url.append("?queryInterceptors=").append(TracingQueryInterceptor.class.getName());
    if (exceptionsTraced) {
      url.append("&exceptionInterceptors=").append(TracingExceptionInterceptor.class.getName());
    }
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
    Tracing.current().close();
    if (connection != null) connection.close();
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
      .extracting(Span::kind)
      .containsExactly(Span.Kind.CLIENT);
  }

  @Test
  public void defaultSpanNameIsOperationName() throws Exception {
    prepareExecuteSelect(QUERY);

    assertThat(spans)
      .extracting(Span::name)
      .containsExactly("select");
  }

  /** This intercepts all SQL, not just queries. This ensures single-word statements work */
  @Test
  public void defaultSpanNameIsOperationName_oneWord() throws Exception {
    connection.setAutoCommit(false);
    connection.commit();

    assertThat(spans)
      .extracting(Span::name)
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
      .extracting(Span::remoteServiceName)
      .contains("myservice");
  }

  @Test
  public void sqlError() throws Exception {
    assertThatThrownBy(() -> prepareExecuteSelect(ERROR_QUERY)).isInstanceOf(SQLException.class);
    assertThat(spans)
      .isNotEmpty();

    if (exceptionsTraced) {
      assertThat(spans)
        .anySatisfy(span -> assertThat(span.tags()).containsEntry("error", "1046"));
    }
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

  Tracing.Builder tracingBuilder(Sampler sampler) {
    return Tracing.newBuilder()
      .spanReporter(spans::add)
      .currentTraceContext(ThreadLocalCurrentTraceContext.create())
      .sampler(sampler);
  }

  static int envOr(String key, int fallback) {
    return System.getenv(key) != null ? Integer.parseInt(System.getenv(key)) : fallback;
  }

  static String envOr(String key, String fallback) {
    return System.getenv(key) != null ? System.getenv(key) : fallback;
  }
}
