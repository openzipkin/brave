/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jdbi3;

import brave.Tracing;
import brave.internal.Nullable;
import org.jdbi.v3.core.statement.SqlLogger;

/**
 * Use this class to decorate your Jdbi3 client and enable Tracing.
 *
 * @since 6.3
 */
public final class JdbiTracing {

  /**
   * @since 6.3
   */
  public static JdbiTracing create(Tracing tracing) {
    return newBuilder(tracing).build();
  }

  /**
   * @since 6.3
   */
  public static Builder newBuilder(Tracing tracing) {
    return new Builder(tracing);
  }

  /**
   * A {@linkplain SqlLogger} that will report to Zipkin how long each query
   * takes.
   *
   * <p>To use it, add it like this:
   * <pre>{@code
   * SqlLogger sqlLogger = JdbiTracing.create(tracing).sqlLogger();
   * jdbi.getConfig(SqlStatements.class).setSqlLogger(sqlLogger);
   * }</pre>
   *
   * @since 6.3
   */
  public SqlLogger sqlLogger() {
    return new TracingSqlLogger(this);
  }

  public static final class Builder {
    final Tracing tracing;
    @Nullable String remoteServiceName;

    Builder(Tracing tracing) {
      if (tracing == null) throw new NullPointerException("tracing == null");
      this.tracing = tracing;
    }

    /**
     * The remote service name that describes the database.
     *
     * <p>Defaults to {@code $databaseType=$databaseName} e.g. "mysql-mydb" or
     * "mysql" if the database name is not available.
     *
     * @since 6.3
     */
    public Builder remoteServiceName(String remoteServiceName) {
      if (remoteServiceName != null && remoteServiceName.isEmpty()) {
        remoteServiceName = null;
      }
      this.remoteServiceName = remoteServiceName;
      return this;
    }

    public JdbiTracing build() {
      return new JdbiTracing(this);
    }
  }

  final Tracing tracing;
  @Nullable final String remoteServiceName;

  JdbiTracing(Builder builder) {
    tracing = builder.tracing;
    remoteServiceName = builder.remoteServiceName;
  }
}
