/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jdbi3;

import brave.handler.MutableSpan;
import brave.propagation.CurrentTraceContext;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.test.ITRemote;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.JdbiException;
import org.jdbi.v3.core.statement.SqlLogger;
import org.jdbi.v3.core.statement.SqlStatements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static brave.Span.Kind.CLIENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

@Tag("docker")
@Testcontainers(disabledWithoutDocker = true)
@Timeout(60)
public class ITJdbiTracing extends ITRemote { // public for invoker test
  static final String QUERY = "select 'hello world'";
  static final String ERROR_QUERY = "select unknown_field FROM unknown_table";

  @Container MySQLContainer mysql = new MySQLContainer();
  SqlLogger sqlLogger = JdbiTracing.create(tracing).sqlLogger();
  Jdbi jdbi;

  @BeforeEach void initClient() {
    jdbi = Jdbi.create(mysql.dataSource());
    jdbi.getConfig(SqlStatements.class).setSqlLogger(sqlLogger);
  }

  @Test void makesChildOfCurrentSpan() {
    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    try (CurrentTraceContext.Scope scope = currentTraceContext.newScope(parent)) {
      prepareExecuteSelect(jdbi, QUERY);
    }

    MutableSpan clientSpan = testSpanHandler.takeRemoteSpan(CLIENT);
    assertChildOf(clientSpan, parent);
  }

  @Test void reportsClientKind() {
    prepareExecuteSelect(jdbi, QUERY);

    testSpanHandler.takeRemoteSpan(CLIENT);
  }

  @Test void defaultSpanNameIsOperationName() {
    prepareExecuteSelect(jdbi, QUERY);

    assertThat(testSpanHandler.takeRemoteSpan(CLIENT).name())
      .isEqualTo("select");
  }

  @Test void addsQueryTag() {
    prepareExecuteSelect(jdbi, QUERY);

    assertThat(testSpanHandler.takeRemoteSpan(CLIENT).tags()).containsOnly(
      entry("sql.query", QUERY)
    );
  }

  @Test void reportsServerAddress() {
    prepareExecuteSelect(jdbi, QUERY);

    MutableSpan span = testSpanHandler.takeRemoteSpan(CLIENT);
    assertThat(span.remoteServiceName()).isEqualTo("mysql-zipkin");
    assertThat(span.remoteIp()).isEqualTo(mysql.host());
    assertThat(span.remotePort()).isEqualTo(mysql.port());
  }

  @Test void setsError() {
    assertThatThrownBy(() -> prepareExecuteSelect(jdbi, ERROR_QUERY)).isInstanceOf(
      JdbiException.class);

    testSpanHandler.takeRemoteSpanWithErrorMessage(CLIENT,
      "Table 'zipkin.unknown_table' doesn't exist");
  }

  void prepareExecuteSelect(Jdbi jdbi, String query) {
    jdbi.useHandle(h -> h.createQuery(query).mapToMap().list());
  }
}
