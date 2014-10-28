package com.github.kristofa.brave.mysql;

import com.github.kristofa.brave.ClientTracer;
import com.google.common.base.Optional;

import java.io.Closeable;
import java.io.IOException;

/**
 * A simple bean whose only purpose in life is to manage the lifecycle of the {@linkplain ClientTracer} in the {@linkplain MySQLStatementInterceptor}.
 */
public class MySQLStatementInterceptorManagementBean implements Closeable {

    public MySQLStatementInterceptorManagementBean(final ClientTracer tracer) {
        MySQLStatementInterceptor.setClientTracer(Optional.of(tracer));
    }

    @Override
    public void close() throws IOException {
        MySQLStatementInterceptor.setClientTracer(Optional.<ClientTracer>absent());
    }
}
