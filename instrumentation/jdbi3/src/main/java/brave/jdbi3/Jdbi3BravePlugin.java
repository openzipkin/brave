/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jdbi3;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.spi.JdbiPlugin;
import org.jdbi.v3.core.statement.SqlStatements;

import brave.Tracing;

/**
 * A Jdbi plugin that will report to Zipkin how long each query takes.
 * * <p>
 * Install by calling
 * {@code jdbi.installPlugin(Jdbi3BravePlugin.newBuilder(tracing).build());}
 */
public final class Jdbi3BravePlugin extends JdbiPlugin.Singleton {
    private final Tracing tracing;

    private Jdbi3BravePlugin(Tracing tracing) {
        if (tracing == null) throw new NullPointerException("tracing == null");
        this.tracing = tracing;
    }

    public static Builder newBuilder(Tracing tracing) {
        return new Builder(tracing);
    }

    @Override
    public void customizeJdbi(Jdbi jdbi) {
        jdbi.getConfig(SqlStatements.class)
            .addContextListener(new BraveStatementContextListener(tracing, new RemoteServiceNameResolver()));
    }

    public static final class Builder {
        private final Tracing tracing;

        public Builder(Tracing tracing) {
            this.tracing = tracing;
        }

        public JdbiPlugin.Singleton build() {
            return new Jdbi3BravePlugin(tracing);
        }
    }
}
