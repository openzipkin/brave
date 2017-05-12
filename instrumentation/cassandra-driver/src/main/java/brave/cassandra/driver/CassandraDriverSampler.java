package brave.cassandra.driver;

import com.datastax.driver.core.Statement;
import javax.annotation.Nullable;

/**
 * Decides whether to start a new trace based on the cassandra statement.
 *
 * <p>Ex. Here's a sampler that only starts traces for bound statements
 * <pre>{@code
 * cassandraDriverTracingBuilder.serverSampler(new CassandraDriverSampler() {
 *   @Override public <Req> Boolean trySample(Statement statement) {
 *     return statement instanceof BoundStatement;
 *   }
 * });
 * }</pre>
 */
// abstract class as it lets us make helpers in the future
public abstract class CassandraDriverSampler {
  /** Ignores the request and uses the {@link brave.sampler.Sampler trace ID instead}. */
  public static final CassandraDriverSampler TRACE_ID = new CassandraDriverSampler() {
    @Override public Boolean trySample(Statement statement) {
      return null;
    }

    @Override public String toString() {
      return "DeferDecision";
    }
  };
  /** Returns false to never start new traces for cassandra client requests. */
  public static final CassandraDriverSampler NEVER_SAMPLE = new CassandraDriverSampler() {
    @Override public Boolean trySample(Statement statement) {
      return false;
    }

    @Override public String toString() {
      return "NeverSample";
    }
  };

  /**
   * Returns an overriding sampling decision for a new trace. Return null ignore the statement and
   * use the {@link brave.sampler.Sampler trace ID sampler}.
   */
  @Nullable public abstract Boolean trySample(Statement statement);
}
