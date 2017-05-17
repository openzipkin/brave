package brave.cassandra;

public final class CassandraTraceKeys {
  /** The name of the trace request, such as "Execute CQL3 query" */
  public static final String CASSANDRA_REQUEST = "cassandra.request";

  public static final String CASSANDRA_SESSION_ID = "cassandra.session_id";

  private CassandraTraceKeys() {
  }
}
