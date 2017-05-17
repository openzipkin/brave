package brave.cassandra.driver;

public final class CassandraTraceKeys {
  public static final String CASSANDRA_KEYSPACE = "cassandra.keyspace";

  /**
   * The CQL query in a statement.  Ex. "select * from customers where id = ?"
   *
   * <p>Used to understand the complexity of a request
   */
  public static final String CASSANDRA_QUERY = "cassandra.query";

  private CassandraTraceKeys() {
  }
}
