package brave.p6spy;

import brave.Span;
import brave.internal.Nullable;
import brave.propagation.ThreadLocalSpan;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.SimpleJdbcEventListener;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TracingJdbcEventListener extends SimpleJdbcEventListener {

  private final static Pattern URL_SERVICE_NAME_FINDER =
      Pattern.compile("zipkinServiceName=(\\w*)");

  @Nullable final String remoteServiceName;
  final boolean includeParameterValues;

  TracingJdbcEventListener(@Nullable String remoteServiceName, boolean includeParameterValues) {
    this.remoteServiceName = remoteServiceName;
    this.includeParameterValues = includeParameterValues;
  }

  /**
   * Uses {@link ThreadLocalSpan} as there's no attribute namespace shared between callbacks, but
   * all callbacks happen on the same thread.
   *
   * <p>Uses {@link ThreadLocalSpan#CURRENT_TRACER} and this interceptor initializes before tracing.
   */
  @Override public void onBeforeAnyExecute(StatementInformation info) {
    String sql = includeParameterValues ? info.getSqlWithValues() : info.getSql();
    // don't start a span unless there is SQL as we cannot choose a relevant name without it
    if (sql == null || sql.isEmpty()) return;

    // Gets the next span (and places it in scope) so code between here and postProcess can read it
    Span span = ThreadLocalSpan.CURRENT_TRACER.next();
    if (span == null || span.isNoop()) return;

    span.kind(Span.Kind.CLIENT).name(sql.substring(0, sql.indexOf(' ')));
    span.tag("sql.query", sql);
    parseServerIpAndPort(info.getConnectionInformation().getConnection(), span);
    span.start();
  }

  @Override public void onAfterAnyExecute(StatementInformation info, long elapsed, SQLException e) {
    Span span = ThreadLocalSpan.CURRENT_TRACER.remove();
    if (span == null || span.isNoop()) return;

    if (e != null) {
      span.tag("error", Integer.toString(e.getErrorCode()));
    }
    span.finish();
  }

  /**
   * This attempts to get the ip and port from the JDBC URL. Ex. localhost and 5555 from {@code
   * jdbc:mysql://localhost:5555/mydatabase}.
   */
  void parseServerIpAndPort(Connection connection, Span span) {
    try {
      String urlAsString = connection.getMetaData().getURL().substring(5); // strip "jdbc:"
      URI url = URI.create(urlAsString.replace(" ", "")); // Remove all white space according to RFC 2396
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
