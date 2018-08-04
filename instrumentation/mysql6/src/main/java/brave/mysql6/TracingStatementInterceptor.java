package brave.mysql6;

import brave.Span;
import brave.propagation.ThreadLocalSpan;
import com.mysql.cj.api.MysqlConnection;
import com.mysql.cj.api.jdbc.JdbcConnection;
import com.mysql.cj.api.jdbc.Statement;
import com.mysql.cj.api.jdbc.interceptors.StatementInterceptor;
import com.mysql.cj.api.log.Log;
import com.mysql.cj.api.mysqla.result.Resultset;
import com.mysql.cj.jdbc.PreparedStatement;
import java.net.URI;
import java.sql.SQLException;
import java.util.Properties;

/**
 * A MySQL statement interceptor that will report to Zipkin how long each statement takes.
 *
 * <p>To use it, append <code>?statementInterceptors=brave.mysql6.TracingStatementInterceptor</code>
 * to the end of the connection url.
 */
public class TracingStatementInterceptor implements StatementInterceptor {

  /**
   * Uses {@link ThreadLocalSpan} as there's no attribute namespace shared between callbacks, but
   * all callbacks happen on the same thread.
   *
   * <p>Uses {@link ThreadLocalSpan#CURRENT_TRACER} and this interceptor initializes before
   * tracing.
   */
  @Override
  public <T extends Resultset> T preProcess(String sql, Statement interceptedStatement) {
    // Gets the next span (and places it in scope) so code between here and postProcess can read it
    Span span = ThreadLocalSpan.CURRENT_TRACER.next();
    if (span == null || span.isNoop()) return null;

    // When running a prepared statement, sql will be null and we must fetch the sql from the statement itself
    if (interceptedStatement instanceof PreparedStatement) {
      sql = ((PreparedStatement) interceptedStatement).getPreparedSql();
    }
    int spaceIndex = sql.indexOf(' '); // Allow span names of single-word statements like COMMIT
    span.kind(Span.Kind.CLIENT).name(spaceIndex == -1 ? sql : sql.substring(0, spaceIndex));
    span.tag("sql.query", sql);
    parseServerIpAndPort(connection, span);
    span.start();
    return null;
  }

  private MysqlConnection connection;

  @Override
  public <T extends Resultset> T postProcess(String sql, Statement interceptedStatement,
      T originalResultSet, int warningCount, boolean noIndexUsed, boolean noGoodIndexUsed,
      Exception statementException) {
    Span span = ThreadLocalSpan.CURRENT_TRACER.remove();
    if (span == null || span.isNoop()) return null;

    if (statementException instanceof SQLException) {
      span.tag("error", Integer.toString(((SQLException) statementException).getErrorCode()));
    }
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
  public StatementInterceptor init(MysqlConnection mysqlConnection, Properties properties,
      Log log) {
    TracingStatementInterceptor interceptor = new TracingStatementInterceptor();
    interceptor.connection = mysqlConnection;
    return interceptor;
  }

  @Override
  public void destroy() {
    // Don't care
  }
}
