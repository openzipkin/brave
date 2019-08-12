/*
 * Copyright 2013-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.mysql8;

import brave.Span;
import brave.propagation.ThreadLocalSpan;
import com.mysql.cj.MysqlConnection;
import com.mysql.cj.Query;
import com.mysql.cj.interceptors.QueryInterceptor;
import com.mysql.cj.jdbc.JdbcConnection;
import com.mysql.cj.log.Log;
import com.mysql.cj.protocol.Resultset;
import com.mysql.cj.protocol.ServerSession;
import java.net.URI;
import java.sql.SQLException;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * A MySQL query interceptor that will report to Zipkin how long each query takes.
 *
 * <p>To use it, append <code>?queryInterceptors=brave.mysql8.TracingQueryInterceptor</code>
 * to the end of the connection url. It is also highly recommended to add
 * <code>&exceptionInterceptors=brave.mysql8.TracingExceptionInterceptor</code> so errors are also
 * included in spans.
 */
public class TracingQueryInterceptor implements QueryInterceptor {

  /**
   * Uses {@link ThreadLocalSpan} as there's no attribute namespace shared between callbacks, but
   * all callbacks happen on the same thread.
   *
   * <p>Uses {@link ThreadLocalSpan#CURRENT_TRACER} and this interceptor initializes before
   * tracing.
   */
  @Override
  public <T extends Resultset> T preProcess(Supplier<String> sqlSupplier, Query interceptedQuery) {
    // Gets the next span (and places it in scope) so code between here and postProcess can read it
    Span span = ThreadLocalSpan.CURRENT_TRACER.next();
    if (span == null || span.isNoop()) return null;

    String sql = sqlSupplier.get();
    int spaceIndex = sql.indexOf(' '); // Allow span names of single-word statements like COMMIT
    span.kind(Span.Kind.CLIENT).name(spaceIndex == -1 ? sql : sql.substring(0, spaceIndex));
    span.tag("sql.query", sql);
    parseServerIpAndPort(connection, span);
    span.start();
    return null;
  }

  private MysqlConnection connection;
  private boolean interceptingExceptions;

  @Override
  public <T extends Resultset> T postProcess(Supplier<String> sql, Query interceptedQuery,
    T originalResultSet, ServerSession serverSession) {
    if (interceptingExceptions && originalResultSet == null) {
      // Error case, the span will be finished in TracingExceptionInterceptor.
      return null;
    }
    Span span = ThreadLocalSpan.CURRENT_TRACER.remove();
    if (span == null || span.isNoop()) return null;

    span.finish();

    return null;
  }

  /**
   * MySQL exposes the host connecting to, but not the port. This attempts to get the port from the
   * JDBC URL. Ex. 5555 from {@code jdbc:mysql://localhost:5555/database}, or 3306 if absent.
   */
  static void parseServerIpAndPort(MysqlConnection connection, Span span) {
    try {
      URI url = URI.create(connection.getURL().substring(5)); // strip "jdbc:"
      String remoteServiceName = connection.getProperties().getProperty("zipkinServiceName");
      if (remoteServiceName == null || "".equals(remoteServiceName)) {
        String databaseName = getDatabaseName(connection);
        if (databaseName != null && !databaseName.isEmpty()) {
          remoteServiceName = "mysql-" + databaseName;
        } else {
          remoteServiceName = "mysql";
        }
      }
      span.remoteServiceName(remoteServiceName);
      String host = getHost(connection);
      if (host != null) {
        span.remoteIpAndPort(host, url.getPort() == -1 ? 3306 : url.getPort());
      }
    } catch (Exception e) {
      // remote address is optional
    }
  }

  private static String getDatabaseName(MysqlConnection connection) throws SQLException {
    if (connection instanceof JdbcConnection) {
      return ((JdbcConnection) connection).getCatalog();
    }
    return "";
  }

  private static String getHost(MysqlConnection connection) {
    if (!(connection instanceof JdbcConnection)) return null;
    return ((JdbcConnection) connection).getHost();
  }

  @Override
  public boolean executeTopLevelOnly() {
    return true;  // True means that we don't get notified about queries that other interceptors issue
  }

  @Override
  public QueryInterceptor init(MysqlConnection mysqlConnection, Properties properties,
    Log log) {
    String exceptionInterceptors = properties.getProperty("exceptionInterceptors");
    TracingQueryInterceptor interceptor = new TracingQueryInterceptor();
    interceptor.connection = mysqlConnection;
    interceptor.interceptingExceptions = exceptionInterceptors != null &&
      exceptionInterceptors.contains(TracingExceptionInterceptor.class.getName());
    if (!interceptor.interceptingExceptions) {
      log.logWarn("TracingExceptionInterceptor not enabled. It is highly recommended to "
        + "enable it for error logging to Zipkin.");
    }
    return interceptor;
  }

  @Override
  public void destroy() {
    // Don't care
  }
}
