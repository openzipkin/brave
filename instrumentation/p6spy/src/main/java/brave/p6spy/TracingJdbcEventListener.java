package brave.p6spy;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.SimpleJdbcEventListener;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import zipkin2.Endpoint;

final class TracingJdbcEventListener extends SimpleJdbcEventListener {

  private final static Pattern URL_SERVICE_NAME_FINDER =
      Pattern.compile("zipkinServiceName=(\\w*)");

  @Nullable final String remoteServiceName;
  final boolean includeParameterValues;

  TracingJdbcEventListener(@Nullable String remoteServiceName, boolean includeParameterValues) {
    this.remoteServiceName = remoteServiceName;
    this.includeParameterValues = includeParameterValues;
  }

  @Override public void onBeforeAnyExecute(StatementInformation info) {
    Tracer tracer = Tracing.currentTracer();
    if (tracer == null) return;
    String sql = includeParameterValues ? info.getSqlWithValues() : info.getSql();
    // don't start a span unless there is SQL as we cannot choose a relevant name without it
    if (sql == null || sql.isEmpty()) return;

    Span span = tracer.nextSpan();
    // regardless of noop or not, set it in scope so that custom contexts can see it (like slf4j)
    if (!span.isNoop()) {
      span.kind(Span.Kind.CLIENT).name(sql.substring(0, sql.indexOf(' ')));
      span.tag("sql.query", sql);
      parseServerAddress(info.getConnectionInformation().getConnection(), span);
      span.start();
    }

    currentSpanInScope.set(tracer.withSpanInScope(span));
  }

  /**
   * There's no attribute namespace shared across request and response. Hence, we need to save off
   * a reference to the span in scope, so that we can close it in the response.
   */
  final ThreadLocal<Tracer.SpanInScope> currentSpanInScope = new ThreadLocal<>();

  @Override public void onAfterAnyExecute(StatementInformation info, long elapsed, SQLException e) {
    Tracer tracer = Tracing.currentTracer();
    if (tracer == null) return;

    Span span = tracer.currentSpan();
    if (span == null) return;
    Tracer.SpanInScope scope = currentSpanInScope.get();
    if (scope != null) {
      scope.close();
      currentSpanInScope.remove();
    }

    if (e != null) {
      span.tag("error", Integer.toString(e.getErrorCode()));
    }
    span.finish();
  }

  /**
   * This attempts to get the ip and port from the JDBC URL. Ex. localhost and 5555 from {@code
   * jdbc:mysql://localhost:5555/mydatabase}.
   */
  void parseServerAddress(Connection connection, Span span) {
    try {
      URI url = URI.create(connection.getMetaData().getURL().substring(5)); // strip "jdbc:"
      String defaultRemoteServiceName = remoteServiceName;
      Matcher matcher = URL_SERVICE_NAME_FINDER.matcher(url.toString());
      if (matcher.find() && matcher.groupCount() == 1) {
        String parsedServiceName = matcher.group(1);
        if (parsedServiceName != null
            && !parsedServiceName.isEmpty()) { // Do not override global service name if parsed service name is invalid
          defaultRemoteServiceName = parsedServiceName;
        }
      }
      Endpoint.Builder builder = Endpoint.newBuilder();
      int port = url.getPort();
      if (port > 0) builder.port(port);
      boolean parsed = builder.parseIp(url.getHost());
      if (defaultRemoteServiceName == null || "".equals(defaultRemoteServiceName)) {
        String databaseName = connection.getCatalog();
        if (databaseName != null && !databaseName.isEmpty()) {
          builder.serviceName(databaseName);
        } else {
          if (!parsed) return;
          builder.serviceName("");
        }
      } else {
        builder.serviceName(defaultRemoteServiceName);
      }
      span.remoteEndpoint(builder.build());
    } catch (Exception e) {
      // remote address is optional
    }
  }
}
