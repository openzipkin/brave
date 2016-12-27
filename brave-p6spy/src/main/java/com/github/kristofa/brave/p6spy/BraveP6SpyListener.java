package com.github.kristofa.brave.p6spy;

import com.github.kristofa.brave.ClientTracer;
import com.p6spy.engine.common.PreparedStatementInformation;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.JdbcEventListener;

import com.twitter.zipkin.gen.Endpoint;
import zipkin.Constants;
import zipkin.TraceKeys;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.sql.SQLException;

public final class BraveP6SpyListener extends JdbcEventListener {
    // TODO: Figure out a better approach
    static volatile ClientTracer clientTracer;

    private int ipv4;
    private int port;
    private final String serviceName;

    public BraveP6SpyListener(P6BraveOptions options) {
        try {
            InetAddress address = Inet4Address.getByName(options.getHost());
            ipv4 = ByteBuffer.wrap(address.getAddress()).getInt();
        } catch (UnknownHostException ignored) {
        }
        try {
            port = Integer.parseInt(options.getPort());
        } catch (Exception ignored) {
        }
        serviceName = options.getServiceName();
    }

    public static void setClientTracer(ClientTracer tracer) {
        clientTracer = tracer;
    }

    @Override
    public void onBeforeExecute(PreparedStatementInformation statementInformation) {
        startPreparedTrace(statementInformation);
    }

    @Override
    public void onAfterExecute(PreparedStatementInformation statementInformation, long timeElapsedNanos, SQLException e) {
        endTrace(e);
    }

    @Override
    public void onBeforeExecute(StatementInformation statementInformation, String sql) {
        startTrace(sql);
    }

    @Override
    public void onAfterExecute(StatementInformation statementInformation, long timeElapsedNanos, String sql, SQLException e) {
        endTrace(e);
    }

    @Override
    public void onBeforeExecuteBatch(StatementInformation statementInformation) {
        startTrace(statementInformation.getSql());
    }

    @Override
    public void onAfterExecuteBatch(StatementInformation statementInformation, long timeElapsedNanos, int[] updateCounts, SQLException e) {
        endTrace(e);
    }

    @Override
    public void onBeforeExecuteUpdate(PreparedStatementInformation statementInformation) {
        startPreparedTrace(statementInformation);
    }

    @Override
    public void onAfterExecuteUpdate(PreparedStatementInformation statementInformation, long timeElapsedNanos, int rowCount, SQLException e) {
        endTrace(e);
    }

    @Override
    public void onBeforeExecuteUpdate(StatementInformation statementInformation, String sql) {
        startTrace(sql);
    }

    @Override
    public void onAfterExecuteUpdate(StatementInformation statementInformation, long timeElapsedNanos, String sql, int rowCount, SQLException e) {
        endTrace(e);
    }

    @Override
    public void onBeforeExecuteQuery(PreparedStatementInformation statementInformation) {
        startPreparedTrace(statementInformation);
    }

    @Override
    public void onAfterExecuteQuery(PreparedStatementInformation statementInformation, long timeElapsedNanos, SQLException e) {
        endTrace(e);
    }

    @Override
    public void onBeforeExecuteQuery(StatementInformation statementInformation, String sql) {
        startTrace(sql);
    }

    @Override
    public void onAfterExecuteQuery(StatementInformation statementInformation, long timeElapsedNanos, String sql, SQLException e) {
        endTrace(e);
    }

    private void startPreparedTrace(PreparedStatementInformation statementInformation) {
        startTrace(statementInformation.getSql());
    }

    private void startTrace(String sql) {
        ClientTracer clientTracer = BraveP6SpyListener.clientTracer;
        if (clientTracer != null) {
            beginTrace(clientTracer, sql);
        }
    }

    private void endTrace(SQLException e) {
        ClientTracer clientTracer = BraveP6SpyListener.clientTracer;
        if (clientTracer != null) {
            endTrace(clientTracer, e);
        }
    }

    private void beginTrace(final ClientTracer tracer, final String sql) {
        tracer.startNewSpan("query");
        tracer.submitBinaryAnnotation(TraceKeys.SQL_QUERY, sql);

        if (ipv4 != 0 && port > 0) {
            tracer.setClientSent(Endpoint.builder()
                .ipv4(ipv4).port(port).serviceName(serviceName).build());
        } else { // logging the server address is optional
            tracer.setClientSent();
        }
    }

    private void endTrace(final ClientTracer tracer, final SQLException statementException) {
        try {
            if (statementException != null) {
                tracer.submitBinaryAnnotation(Constants.ERROR, Integer.toString(statementException.getErrorCode()));
            }
        } finally {
            tracer.setClientReceived();
        }
    }
}
