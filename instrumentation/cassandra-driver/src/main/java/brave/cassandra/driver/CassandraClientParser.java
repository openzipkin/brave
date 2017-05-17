package brave.cassandra.driver;

import brave.SpanCustomizer;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Statement;
import com.google.common.base.CaseFormat;

import static zipkin.Constants.ERROR;

/**
 * Provides reasonable defaults for the data contained in cassandra client spans. Subclass to
 * customize, for example, to add tags based on response headers.
 */
public class CassandraClientParser {

  /**
   * Override to change what data from the statement are parsed into the span representing it. By
   * default, this sets the span name to the lower-camel case type name and tags {@link
   * CassandraTraceKeys#CASSANDRA_KEYSPACE} and {@link CassandraTraceKeys#CASSANDRA_QUERY} for bound
   * statements.
   *
   * <p>If you only want to change the span name, you can override {@link #spanName(Statement)}
   * instead.
   *
   * @see #spanName(Statement)
   */
  public void request(Statement statement, SpanCustomizer customizer) {
    customizer.name(spanName(statement));
    String keyspace = statement.getKeyspace();
    if (keyspace != null) {
      customizer.tag(CassandraTraceKeys.CASSANDRA_KEYSPACE, statement.getKeyspace());
    }
    if (statement instanceof BoundStatement) {
      customizer.tag(CassandraTraceKeys.CASSANDRA_QUERY,
          ((BoundStatement) statement).preparedStatement().getQueryString());
    }
  }

  /** Returns the span name of the statement. Defaults to the lower-camel case type name. */
  protected String spanName(Statement statement) {
    return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, statement.getClass().getSimpleName());
  }

  /** Override to parse data from the result set into the span modeling it. */
  public void response(ResultSet resultSet, SpanCustomizer customizer) {
  }

  /**
   * Override to change what data from an error are parsed into the span modeling it. Defaults to
   * the throwable's message, or the simple name of the throwable's type.
   */
  public void error(Throwable throwable, SpanCustomizer customizer) {
    String message = throwable.getMessage();
    customizer.tag(ERROR, message != null ? message : throwable.getClass().getSimpleName());
  }
}
