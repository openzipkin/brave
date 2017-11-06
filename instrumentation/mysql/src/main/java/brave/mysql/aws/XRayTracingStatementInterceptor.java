package brave.mysql.aws;

import brave.Span;
import brave.mysql.TracingStatementInterceptor;
import com.mysql.jdbc.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class XRayTracingStatementInterceptor extends TracingStatementInterceptor {

  private final static Logger LOGGER = Logger.getLogger(XRayTracingStatementInterceptor.class.getName());

  private final boolean useQueryForUrl;

  public XRayTracingStatementInterceptor() {
    this(false);
  }

  public XRayTracingStatementInterceptor(boolean useQueryForUrl) {
    this.useQueryForUrl = useQueryForUrl;
  }

  @Override
  protected void doPreProcessTag(Connection connection, String sql, Span span) {
    super.doPreProcessTag(connection, sql, span);
    DatabaseMetaData m;
    try {
      m = connection.getMetaData();
      span.tag("sql.url", useQueryForUrl ? sql : m.getURL());
      span.tag("sql.database_type", m.getDatabaseProductName());
      span.tag("sql.database_version", m.getDatabaseProductVersion());
      span.tag("sql.driver_version", m.getDriverVersion());
      span.tag("sql.user", m.getUserName());
      span.tag("sql.query", sql);
    } catch (SQLException e) {
      LOGGER.log(Level.WARNING, "DatabaseMetaData could not be get.");
    }

  }
}