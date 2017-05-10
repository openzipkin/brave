package brave.http;

import brave.internal.Nullable;

/**
 * Decides whether to start a new trace based on http request properties such as path.
 *
 * <p>Ex. Here's a sampler that only traces api requests
 * <pre>{@code
 * httpTracingBuilder.serverSampler(new HttpSampler() {
 *   @Override public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request) {
 *     return adapter.path(request).startsWith("/api");
 *   }
 * });
 * }</pre>
 */
// abstract class as you can't lambda generic methods anyway. This lets us make helpers in the future
public abstract class HttpSampler {
  /** Ignores the request and uses the {@link brave.sampler.Sampler trace ID instead}. */
  public static final HttpSampler TRACE_ID = new HttpSampler() {
    @Override public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request) {
      return null;
    }

    @Override public String toString() {
      return "DeferDecision";
    }
  };
  /**
   * Returns false to never start new traces for http requests. This could make sense for http
   * clients. For example, you may wish to never capture http traces unless they originated for a
   * server request. For example, this would filter out client requests made during bootstrap.
   */
  public static final HttpSampler NEVER_SAMPLE = new HttpSampler() {
    @Override public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request) {
      return false;
    }

    @Override public String toString() {
      return "NeverSample";
    }
  };

  /**
   * Returns an overriding sampling decision for a new trace. Return null ignore the request and use
   * the {@link brave.sampler.Sampler trace ID sampler}.
   */
  @Nullable public abstract <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request);
}
