/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
package brave.p6spy;

import brave.Span;
import brave.internal.Nullable;
import brave.propagation.ThreadLocalSpan;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.SimpleJdbcEventListener;
import com.p6spy.engine.logging.P6LogLoadableOptions;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static brave.Span.Kind.CLIENT;

final class TracingJdbcEventListener extends SimpleJdbcEventListener {

  // Captures all the characters between = and either the next & or the end of the string.
  private static final Pattern URL_SERVICE_NAME_FINDER =
    Pattern.compile("zipkinServiceName=(.*?)(?:&|$)");

  @Nullable final String remoteServiceName;
  final boolean includeParameterValues;
  final P6LogLoadableOptions logOptions;

  TracingJdbcEventListener(@Nullable String remoteServiceName, boolean includeParameterValues,
    P6LogLoadableOptions logOptions) {
    this.remoteServiceName = remoteServiceName;
    this.includeParameterValues = includeParameterValues;
    this.logOptions = logOptions;
  }

  /**
   * Uses {@link ThreadLocalSpan} as there's no attribute namespace shared between callbacks, but
   * all callbacks happen on the same thread.
   *
   * <p>Uses {@link ThreadLocalSpan#CURRENT_TRACER} and this interceptor initializes before
   * tracing.
   */
  @Override public void onBeforeAnyExecute(StatementInformation info) {
    String sql = includeParameterValues ? info.getSqlWithValues() : info.getSql();
    if (!isLoggable(sql)) return;

    // Gets the next span (and places it in scope) so code between here and postProcess can read it
    Span span = ThreadLocalSpan.CURRENT_TRACER.next();
    if (span == null || span.isNoop()) return;

    int spaceIndex = sql.indexOf(' '); // Allow span names of single-word statements like COMMIT
    span.kind(CLIENT).name(spaceIndex == -1 ? sql : sql.substring(0, spaceIndex));
    span.tag("sql.query", sql);
    parseServerIpAndPort(info.getConnectionInformation().getConnection(), span);
    span.start();
  }

  @Override public void onAfterAnyExecute(StatementInformation info, long elapsed, SQLException e) {
    Span span = ThreadLocalSpan.CURRENT_TRACER.remove();
    if (span == null || span.isNoop()) return;

    if (e != null) {
      span.error(e);
      span.tag("error", Integer.toString(e.getErrorCode()));
    }
    span.finish();
  }

  boolean isLoggable(String sql) {
    // don't start a span unless there is SQL as we cannot choose a relevant name without it
    // empty batches and connection commits/rollbacks
    if (sql == null || sql.isEmpty()) {
      return false;
    }
    if (!logOptions.getFilter()) {
      return true;
    }

    final Pattern sqlExpressionPattern = logOptions.getSQLExpressionPattern();
    final Pattern includeExcludePattern = logOptions.getIncludeExcludePattern();

    return (sqlExpressionPattern == null || sqlExpressionPattern.matcher(sql).matches())
      && (includeExcludePattern == null || includeExcludePattern.matcher(sql).matches());
  }

  /**
   * This attempts to get the ip and port from the JDBC URL. Ex. localhost and 5555 from {@code
   * jdbc:mysql://localhost:5555/mydatabase}.
   */
  void parseServerIpAndPort(Connection connection, Span span) {
    try {
      String urlAsString = connection.getMetaData().getURL().substring(5); // strip "jdbc:"
      URI url =
        URI.create(urlAsString.replace(" ", "")); // Remove all white space according to RFC 2396
      String defaultRemoteServiceName = remoteServiceName;
      Matcher matcher = URL_SERVICE_NAME_FINDER.matcher(url.toString());
      if (matcher.find() && matcher.groupCount() == 1) {
        String parsedServiceName = matcher.group(1);
        if (parsedServiceName != null
          && !parsedServiceName.isEmpty()) { // Do not override global service name if parsed service name is invalid
          defaultRemoteServiceName = parsedServiceName;
        }
      }
      if (defaultRemoteServiceName == null || "".equals(defaultRemoteServiceName)) {
        String databaseName = connection.getCatalog();
        if (databaseName != null && !databaseName.isEmpty()) {
          span.remoteServiceName(databaseName);
        }
      } else {
        span.remoteServiceName(defaultRemoteServiceName);
      }
      span.remoteIpAndPort(url.getHost(), url.getPort());
    } catch (Exception e) {
      // remote address is optional
    }
  }
}
