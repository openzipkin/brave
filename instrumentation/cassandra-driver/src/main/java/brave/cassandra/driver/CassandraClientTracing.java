package brave.cassandra.driver;

import brave.Tracing;
import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;
import zipkin.Endpoint;

@AutoValue
public abstract class CassandraClientTracing {
  public static CassandraClientTracing create(Tracing tracing) {
    return newBuilder(tracing).build();
  }

  public static Builder newBuilder(Tracing tracing) {
    return new AutoValue_CassandraClientTracing.Builder()
        .tracing(tracing)
        .parser(new CassandraClientParser())
        .sampler(CassandraClientSampler.TRACE_ID);
  }

  public abstract Tracing tracing();

  public abstract CassandraClientParser parser();

  /**
   * Used by cassandra clients to indicate the name of the destination service. Defaults to the
   * cluster name.
   *
   * <p>As this is endpoint-specific, it is typical to create a scoped instance of {@linkplain
   * CassandraClientTracing} to assign this value.
   *
   * For example:
   * <pre>{@code
   * production = TracingSession.create(httpTracing.remoteServiceName("production"));
   * }</pre>
   *
   * @see zipkin.Constants#SERVER_ADDR
   * @see brave.Span#remoteEndpoint(Endpoint)
   */
  @Nullable public abstract String remoteServiceName();

  /**
   * Scopes this component for a client of the indicated server.
   *
   * @see #remoteServiceName()
   */
  public CassandraClientTracing clientOf(String remoteServiceName) {
    return toBuilder().remoteServiceName(remoteServiceName).build();
  }

  /**
   * Returns an overriding sampling decision for a new trace. Defaults to ignore the request and use
   * the {@link CassandraClientSampler#TRACE_ID trace ID instead}.
   */
  public abstract CassandraClientSampler sampler();

  public abstract Builder toBuilder();

  @AutoValue.Builder
  public static abstract class Builder {
    /** @see CassandraClientTracing#tracing() */
    public abstract Builder tracing(Tracing tracing);

    /** @see CassandraClientTracing#parser() */
    public abstract Builder parser(CassandraClientParser parser);

    /** @see CassandraClientTracing#sampler() */
    public abstract Builder sampler(CassandraClientSampler sampler);

    public abstract CassandraClientTracing build();

    abstract Builder remoteServiceName(@Nullable String remoteServiceName);

    Builder() {
    }
  }

  CassandraClientTracing() {
  }
}
