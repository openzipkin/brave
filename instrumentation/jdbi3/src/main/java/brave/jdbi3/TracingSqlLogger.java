/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jdbi3;

import brave.Span;
import brave.Tracing;
import brave.propagation.ThreadLocalSpan;
import java.net.URI;
import java.sql.SQLException;
import org.jdbi.v3.core.statement.SqlLogger;
import org.jdbi.v3.core.statement.SqlStatements;
import org.jdbi.v3.core.statement.StatementContext;

import static brave.Span.Kind.CLIENT;

final class TracingSqlLogger implements SqlLogger {
  static final String JDBC_PREFIX = "jdbc:";

  final Tracing tracing;
  final ThreadLocalSpan threadLocalSpan;
  final String remoteServiceName;

  TracingSqlLogger(JdbiTracing jdbiTracing) {
    this.tracing = jdbiTracing.tracing;
    this.remoteServiceName = jdbiTracing.remoteServiceName;
    this.threadLocalSpan = ThreadLocalSpan.create(tracing.tracer());
  }

  @Override public void logBeforeExecution(StatementContext ctx) {
    Span span = threadLocalSpan.next().kind(CLIENT);
    if (span == null || span.isNoop()) return;
    ctx.setTraceId(span.context().traceIdString());

    final String renderedSql = ctx.getRenderedSql();
    if (renderedSql != null) {
      String sql = renderedSql.substring(
        0,
        Math.min(renderedSql.length(), ctx.getConfig(SqlStatements.class).getJfrSqlMaxLength())
      );
      int spaceIndex = sql.indexOf(' '); // Allow span names of single-word statements like COMMIT
      span.name(spaceIndex == -1 ? sql : sql.substring(0, spaceIndex));
      span.tag("sql.query", sql);
    } else {
      span.name(ctx.describeJdbiStatementType());
    }

    span.remoteServiceName(remoteServiceName);
    if (ctx.getConnection() != null) {
      try {
        String jdbcUrl = ctx.getConnection().getMetaData().getURL();
        parseServerIpAndPort(jdbcUrl, span, remoteServiceName == null);
      } catch (SQLException ignored) {
      }
    }

    span.start();
  }

  @Override public void logAfterExecution(StatementContext ctx) {
    Span span = threadLocalSpan.remove();
    if (span == null) return;
    span.finish();
  }

  @Override public void logException(StatementContext ctx, SQLException ex) {
    Span span = threadLocalSpan.remove();
    if (span == null) return;
    span.error(ex);
    span.finish();
  }

  static void parseServerIpAndPort(String jdbcUrl, Span span, boolean addRemoteServiceName) {
    if (!jdbcUrl.startsWith(JDBC_PREFIX)) return; // unlikely, and no way to parse it

    URI url = URI.create(jdbcUrl.substring(JDBC_PREFIX.length()));
    if (url.getHost() != null) {
      span.remoteIpAndPort(url.getHost(), url.getPort());
    }
    if (addRemoteServiceName) {
      if (url.getPath() != null) {
        // e.g. mysql://localhost:3306/testdb -> mysql-testdb
        span.remoteServiceName(url.getScheme() + "-" + url.getPath().replace("/", ""));
      } else if (url.getSchemeSpecificPart() != null) {
        // e.g. h2:mem:testdb -> h2-testdb
        String ssp = url.getSchemeSpecificPart();
        int lastColon = ssp.lastIndexOf(':');
        ssp = lastColon != -1 ? ssp.substring(lastColon + 1) : ssp;
        span.remoteServiceName(url.getScheme() + "-" + ssp);
      } else {
        span.remoteServiceName(url.getScheme());
      }
    }
  }
}
