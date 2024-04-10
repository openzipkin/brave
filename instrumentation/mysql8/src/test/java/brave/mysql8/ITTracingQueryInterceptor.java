/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.mysql8;

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
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static brave.Span.Kind.CLIENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ITTracingQueryInterceptor {
  static final String QUERY = "select 'hello world'";
  static final String ERROR_QUERY = "select unknown_field FROM unknown_table";

  public static Iterable<Boolean> exceptionsTraced() {
    return Arrays.asList(false, true);
  }
  public boolean exceptionsTraced;

  StrictCurrentTraceContext currentTraceContext = StrictCurrentTraceContext.create();
  TestSpanHandler spans = new TestSpanHandler();
  Tracing tracing = Tracing.newBuilder()
      .currentTraceContext(currentTraceContext).addSpanHandler(spans).build();

  Connection connection;

  @BeforeEach void init() throws SQLException {
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
    assumeTrue(dataSource.getUser() != null,
      "Minimally, the environment variable MYSQL_USER must be set");
    dataSource.setPassword(envOr("MYSQL_PASS", ""));
    connection = dataSource.getConnection();
    spans.clear();
  }

  @AfterEach void close() throws SQLException {
    if (connection != null) connection.close();
    tracing.close();
    currentTraceContext.close();
  }

  @MethodSource("exceptionsTraced")
  @ParameterizedTest(name = "exceptions traced: {0}")
  void makesChildOfCurrentSpan(boolean exceptionsTraced) throws Exception {
    initITTracingQueryInterceptor(exceptionsTraced);
    ScopedSpan parent = tracing.tracer().startScopedSpan("test");
    try {
      prepareExecuteSelect(QUERY);
    } finally {
      parent.finish();
    }

    assertThat(spans)
      .hasSize(2);
  }

  @MethodSource("exceptionsTraced")
  @ParameterizedTest(name = "exceptions traced: {0}")
  void reportsClientKindToZipkin(boolean exceptionsTraced) throws Exception {
    initITTracingQueryInterceptor(exceptionsTraced);
    prepareExecuteSelect(QUERY);

    assertThat(spans)
      .extracting(MutableSpan::kind)
      .containsExactly(CLIENT);
  }

  @MethodSource("exceptionsTraced")
  @ParameterizedTest(name = "exceptions traced: {0}")
  void defaultSpanNameIsOperationName(boolean exceptionsTraced) throws Exception {
    initITTracingQueryInterceptor(exceptionsTraced);
    prepareExecuteSelect(QUERY);

    assertThat(spans)
      .extracting(MutableSpan::name)
      .containsExactly("select");
  }

  /** This intercepts all SQL, not just queries. This ensures single-word statements work */
  @MethodSource("exceptionsTraced")
  @ParameterizedTest(name = "exceptions traced: {0}")
  void defaultSpanNameIsOperationName_oneWord(boolean exceptionsTraced) throws Exception {
    initITTracingQueryInterceptor(exceptionsTraced);
    connection.setAutoCommit(false);
    connection.commit();

    assertThat(spans)
      .extracting(MutableSpan::name)
      .contains("commit");
  }

  @MethodSource("exceptionsTraced")
  @ParameterizedTest(name = "exceptions traced: {0}")
  void addsQueryTag(boolean exceptionsTraced) throws Exception {
    initITTracingQueryInterceptor(exceptionsTraced);
    prepareExecuteSelect(QUERY);

    assertThat(spans)
      .flatExtracting(s -> s.tags().entrySet())
      .containsExactly(entry("sql.query", QUERY));
  }

  @MethodSource("exceptionsTraced")
  @ParameterizedTest(name = "exceptions traced: {0}")
  void reportsServerAddress(boolean exceptionsTraced) throws Exception {
    initITTracingQueryInterceptor(exceptionsTraced);
    prepareExecuteSelect(QUERY);

    assertThat(spans)
      .extracting(MutableSpan::remoteServiceName)
      .contains("myservice");
  }

  @MethodSource("exceptionsTraced")
  @ParameterizedTest(name = "exceptions traced: {0}")
  void sqlError(boolean exceptionsTraced) throws Exception {
    initITTracingQueryInterceptor(exceptionsTraced);
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

  static int envOr(String key, int fallback) {
    return System.getenv(key) != null ? Integer.parseInt(System.getenv(key)) : fallback;
  }

  static String envOr(String key, String fallback) {
    return System.getenv(key) != null ? System.getenv(key) : fallback;
  }

  public void initITTracingQueryInterceptor(boolean exceptionsTraced) {
    this.exceptionsTraced = exceptionsTraced;
  }
}
