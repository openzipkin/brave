package brave.mysql6;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import com.mysql.cj.api.MysqlConnection;
import com.mysql.cj.api.jdbc.JdbcConnection;
import com.mysql.cj.api.jdbc.Statement;
import com.mysql.cj.api.jdbc.interceptors.StatementInterceptor;
import com.mysql.cj.api.log.Log;
import com.mysql.cj.api.mysqla.result.Resultset;
import java.net.URI;
import java.sql.SQLException;
import java.util.Properties;

import com.mysql.cj.jdbc.PreparedStatement;
import zipkin.Constants;
import zipkin.Endpoint;
import zipkin.TraceKeys;

/**
 * A MySQL statement interceptor that will report to Zipkin how long each statement takes.
 *
 * <p>To use it, append <code>?statementInterceptors=brave.mysql6.TracingStatementInterceptor</code>
 * to the end of the connection url.
 */
public class TracingStatementInterceptor implements StatementInterceptor {


  @Override
  public Resultset preProcess(String sql, Statement interceptedStatement) throws SQLException {
    Tracer tracer = Tracing.currentTracer();
    if (tracer == null) return null;

    Span span = tracer.nextSpan();
    // regardless of noop or not, set it in scope so that custom contexts can see it (like slf4j)
    if (!span.isNoop()) {
      // When running a prepared statement, sql will be null and we must fetch the sql from the statement itself
      if (interceptedStatement instanceof PreparedStatement) {
        sql = ((PreparedStatement) interceptedStatement).getPreparedSql();
      }
      int spaceIndex = sql.indexOf(' '); // Allow span names of single-word statements like COMMIT
      span.kind(Span.Kind.CLIENT).name(spaceIndex == -1 ? sql : sql.substring(0, spaceIndex));
      span.tag(TraceKeys.SQL_QUERY, sql);
      parseServerAddress(connection, span);
      span.start();
    }

    currentSpanInScope.set(tracer.withSpanInScope(span));

    return null;
  }

  /**
   * There's no attribute namespace shared across request and response. Hence, we need to save off
   * a reference to the span in scope, so that we can close it in the response.
   */
  final ThreadLocal<Tracer.SpanInScope> currentSpanInScope = new ThreadLocal<>();
  private MysqlConnection connection;

  @Override
  public <T extends Resultset> T postProcess(String sql, Statement interceptedStatement, T originalResultSet, int warningCount, boolean noIndexUsed, boolean noGoodIndexUsed, Exception statementException) throws SQLException {
    Tracer tracer = Tracing.currentTracer();
    if (tracer == null) return null;

    Span span = tracer.currentSpan();
    if (span == null) return null;
    currentSpanInScope.get().close();
    currentSpanInScope.remove();

    if (statementException != null && statementException instanceof SQLException) {
      span.tag(Constants.ERROR, Integer.toString(((SQLException)statementException).getErrorCode()));
    }
    span.finish();

    return null;
  }

  /**
   * MySQL exposes the host connecting to, but not the port. This attempts to get the port from the
   * JDBC URL. Ex. 5555 from {@code jdbc:mysql://localhost:5555/database}, or 3306 if absent.
   */
  static void parseServerAddress(MysqlConnection connection, Span span) {
    try {
      URI url = URI.create(connection.getURL().substring(5)); // strip "jdbc:"
      int port = url.getPort() == -1 ? 3306 : url.getPort();
      String remoteServiceName = connection.getProperties().getProperty("zipkinServiceName");
      if (remoteServiceName == null || "".equals(remoteServiceName)) {
        String databaseName = getDatabaseName(connection);
        if (databaseName != null && !databaseName.isEmpty()) {
          remoteServiceName = "mysql-" + databaseName;
        } else {
          remoteServiceName = "mysql";
        }
      }
      Endpoint.Builder builder = Endpoint.builder().serviceName(remoteServiceName).port(port);
      if (!builder.parseIp(getHost(connection))) return;
      span.remoteEndpoint(builder.build());
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

  private static String getHost(MysqlConnection connection) throws SQLException {
      if (connection instanceof JdbcConnection) {
          return ((JdbcConnection) connection).getHost();
      }
      return "";
  }

  @Override
  public boolean executeTopLevelOnly() {
    return true;  // True means that we don't get notified about queries that other interceptors issue
  }

  @Override
  public StatementInterceptor init(MysqlConnection mysqlConnection, Properties properties, Log log) {
    TracingStatementInterceptor interceptor = new TracingStatementInterceptor();
    interceptor.connection = mysqlConnection;
    return interceptor;
  }

  @Override
  public void destroy() {
    // Don't care
  }
}
