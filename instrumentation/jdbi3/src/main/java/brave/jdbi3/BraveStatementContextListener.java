/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jdbi3;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;

import org.jdbi.v3.core.statement.SqlStatements;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.core.statement.StatementContextListener;

import brave.Span;
import brave.Tracing;

public class BraveStatementContextListener implements StatementContextListener {
    private final Tracing tracing;
    private final RemoteServiceNameResolver remoteServiceNameResolver;

    public BraveStatementContextListener(Tracing tracing, RemoteServiceNameResolver remoteServiceNameResolver) { this.tracing = tracing;
        this.remoteServiceNameResolver = remoteServiceNameResolver;
    }

    @Override
    public void contextCreated(final StatementContext ctx) {
        String spanName = ctx.describeJdbiStatementType();
        Span span = tracing.tracer().nextSpan().name(spanName);

        Instant start = ctx.getExecutionMoment();
        if (start != null) {
            span.start(start.toEpochMilli() * 1000);
        } else {
            span.start();
        }

        ctx.setTraceId(span.context().traceIdString());

        ctx.addCleanable(() -> {
            final SqlStatements stmtConfig = ctx.getConfig(SqlStatements.class);

            String remoteServiceName = getRemoteServiceName(ctx);
            if (remoteServiceName != null && !remoteServiceName.isEmpty()) {
                span.remoteServiceName(remoteServiceName);
            }

            span.kind(Span.Kind.CLIENT);

            final String renderedSql = ctx.getRenderedSql();
            if (renderedSql != null) {
                String truncated = renderedSql.substring(
                    0,
                    Math.min(renderedSql.length(), stmtConfig.getJfrSqlMaxLength())
                );
                span.tag("sql.query", truncated);
            }

            span.tag("sql.rows", Long.toString(ctx.getMappedRows()));

            if (ctx.getCompletionMoment() == null) {
                span.tag("error", "");
            }

            if (ctx.getCompletionMoment() != null) {
                span.finish(ctx.getCompletionMoment().toEpochMilli() * 1000);
            } else if (ctx.getExceptionMoment() != null) {
                span.finish(ctx.getExceptionMoment().toEpochMilli() * 1000);
            } else {
                span.finish();
            }
        });
    }

    String getRemoteServiceName(StatementContext ctx) {
        Connection connection = ctx.getConnection();
        if (connection == null) {
            return null;
        }
        try {
            String url = connection.getMetaData().getURL();
            return remoteServiceNameResolver.resolve(url);
        } catch (SQLException ignored) {
            return null;
        }
    }
}
