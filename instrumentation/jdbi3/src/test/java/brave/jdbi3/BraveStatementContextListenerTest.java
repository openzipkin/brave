/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jdbi3;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.jdbi.v3.core.ConnectionFactory;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.SqlStatements;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.TraceContext;

public class BraveStatementContextListenerTest {
    @Test
    public void shouldReportSpans() throws SQLException {
        Span span = Mockito.mock(Span.class);
        Tracing tracing = buildTracing(span);
        Jdbi jdbi = Jdbi.create(buildConnectionFactory());

        jdbi.getConfig(SqlStatements.class).addContextListener(new BraveStatementContextListener(tracing, new RemoteServiceNameResolver()));
        jdbi.useHandle(handle -> {
            handle.execute("INSERT INTO testdb.testdb (id, name) VALUES (1, 'testdb')");
        });

        Mockito.verify(span).name("Update");
    }

    private static ConnectionFactory buildConnectionFactory() throws SQLException {
        DatabaseMetaData databaseMetaData = Mockito.mock(DatabaseMetaData.class);
        Mockito.when(databaseMetaData.getURL()).thenReturn("jdbc:mysql://localhost:3306/testdb");
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(connection.getMetaData()).thenReturn(databaseMetaData);
        Mockito.when(connection.prepareStatement(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(Mockito.mock(PreparedStatement.class));
        ConnectionFactory connectionFactory = () -> connection;
        return connectionFactory;
    }

    private static Tracing buildTracing(Span span) {
        TraceContext traceContext = Mockito.mock(TraceContext.class);
        Mockito.when(span.name(Mockito.anyString())).thenReturn(span);
        Mockito.when(span.context()).thenReturn(traceContext);
        Tracer tracer = Mockito.mock(Tracer.class);
        Mockito.when(tracer.nextSpan()).thenReturn(span);
        Tracing tracing = Mockito.mock(Tracing.class);
        Mockito.when(tracing.tracer()).thenReturn(tracer);
        return tracing;
    }
}