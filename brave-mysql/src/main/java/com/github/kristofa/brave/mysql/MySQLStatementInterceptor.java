package com.github.kristofa.brave.mysql;

import com.github.kristofa.brave.ClientTracer;
import com.mysql.jdbc.Connection;
import com.mysql.jdbc.PreparedStatement;
import com.mysql.jdbc.ResultSetInternalMethods;
import com.mysql.jdbc.Statement;
import com.mysql.jdbc.StatementInterceptorV2;

import java.sql.SQLException;
import java.util.Properties;

/**
 * <p>
 *     A MySQL statement interceptor that will report to Zipkin how long each statement takes.
 * </p>
 * <p>
 *     To use it, append <code>?statementInterceptors=com.github.kristofa.brave.mysql.MySQLStatementInterceptor</code> to the end of the connection url.
 * </p>
 * <p>
 *     Note that this class must be injected with the {@linkplain ClientTracer} to use to communicate with Zipkin via the {@linkplain #setClientTracer} method;
 *     this is normally done by the {@linkplain MySQLStatementInterceptorManagementBean}.
 * </p>
 */
public class MySQLStatementInterceptor implements StatementInterceptorV2 {

    // TODO: is static scope best? ex. preferred to thread local etc?
    static volatile ClientTracer clientTracer;

    public static void setClientTracer(final ClientTracer tracer) {
        clientTracer = tracer;
    }

    @Override
    public ResultSetInternalMethods preProcess(final String sql, final Statement interceptedStatement, final Connection connection) throws SQLException {

        ClientTracer clientTracer = this.clientTracer;
        if (clientTracer != null) {
            final String sqlToLog;
            // When running a prepared statement, sql will be null and we must fetch the sql from the statement itself
            if (interceptedStatement instanceof PreparedStatement) {
                sqlToLog = ((PreparedStatement) interceptedStatement).getPreparedSql();
            } else {
                sqlToLog = sql;
            }

            beginTrace(clientTracer, sqlToLog, connection);
        }

        return null;
    }

    @Override
    public ResultSetInternalMethods postProcess(final String sql, final Statement interceptedStatement, final ResultSetInternalMethods originalResultSet,
                                                final Connection connection, final int warningCount, final boolean noIndexUsed, final boolean noGoodIndexUsed,
                                                final SQLException statementException) throws SQLException {

        ClientTracer clientTracer = this.clientTracer;
        if (clientTracer != null) {
            endTrace(clientTracer, warningCount, statementException);
        }

        return null;
    }

    private void beginTrace(final ClientTracer tracer, final String sql, final Connection connection) throws SQLException {
        final String schema = connection.getSchema();
        tracer.startNewSpan("query");
        tracer.setCurrentClientServiceName(schema == null ? "MySQL" : schema);
        tracer.submitBinaryAnnotation("executed.query", sql);
        tracer.setClientSent();
    }

    private void endTrace(final ClientTracer tracer, final int warningCount, final SQLException statementException) {
        try {
            if (warningCount > 0) {
                tracer.submitBinaryAnnotation("warning.count", warningCount);
            }
            if (statementException != null) {
                tracer.submitBinaryAnnotation("error.code", statementException.getErrorCode());
            }
        } finally {
            tracer.setClientReceived();
        }
    }

    @Override
    public boolean executeTopLevelOnly() {
        // True means that we don't get notified about queries that other interceptors issue
        return true;
    }

    @Override
    public void init(final Connection connection, final Properties properties) throws SQLException {
        // Don't care
    }

    @Override
    public void destroy() {
        // Don't care
    }
}
