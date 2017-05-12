package brave.cassandra.driver;

import brave.Tracing;
import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;
import zipkin.Endpoint;

@AutoValue
public abstract class CassandraDriverTracing {
  public static CassandraDriverTracing create(Tracing tracing) {
    return newBuilder(tracing).build();
  }

  public static Builder newBuilder(Tracing tracing) {
    return new AutoValue_CassandraDriverTracing.Builder()
        .tracing(tracing)
        .parser(new CassandraDriverParser())
        .sampler(CassandraDriverSampler.TRACE_ID);
  }

  public abstract Tracing tracing();

  public abstract CassandraDriverParser parser();

  /**
   * Used by cassandra clients to indicate the name of the destination service. Defaults to the
   * cluster name.
   *
   * <p>As this is endpoint-specific, it is typical to create a scoped instance of {@linkplain
   * CassandraDriverTracing} to assign this value.
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
  public CassandraDriverTracing clientOf(String remoteServiceName) {
    return toBuilder().remoteServiceName(remoteServiceName).build();
  }

  /**
   * Returns an overriding sampling decision for a new trace. Defaults to ignore the request and use
   * the {@link CassandraDriverSampler#TRACE_ID trace ID instead}.
   */
  public abstract CassandraDriverSampler sampler();

  public abstract Builder toBuilder();

  @AutoValue.Builder
  public static abstract class Builder {
    /** @see CassandraDriverTracing#tracing() */
    public abstract Builder tracing(Tracing tracing);

    /** @see CassandraDriverTracing#parser() */
    public abstract Builder parser(CassandraDriverParser parser);

    /** @see CassandraDriverTracing#sampler() */
    public abstract Builder sampler(CassandraDriverSampler sampler);

    public abstract CassandraDriverTracing build();

    abstract Builder remoteServiceName(@Nullable String remoteServiceName);

    Builder() {
    }
  }

  CassandraDriverTracing() {
  }
}
