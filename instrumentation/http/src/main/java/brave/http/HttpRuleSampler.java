/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.http;

import brave.Tracing;
import brave.sampler.Matcher;
import brave.sampler.ParameterizedSampler;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunction;

/**
 * Assigns sample rates to http routes.
 *
 * <p>Ex. Here's a sampler that traces 100 requests per second to /foo and 10 POST requests to /bar
 * per second. This doesn't start new traces for requests to favicon (which many browsers
 * automatically fetch). Other requests will use a global rate provided by the {@link Tracing
 * tracing component}.
 *
 * <p><pre>{@code
 * import static brave.http.HttpRequestMatchers.methodIsEqualTo;
 * import static brave.http.HttpRequestMatchers.pathStartsWith;
 * import static brave.sampler.Matchers.and;
 *
 * httpTracingBuilder.serverSampler(HttpRuleSampler.newBuilder()
 *   .putRule(pathStartsWith("/favicon"), Sampler.NEVER_SAMPLE)
 *   .putRule(pathStartsWith("/foo"), RateLimitingSampler.create(100))
 *   .putRule(and(methodIsEqualTo("POST"), pathStartsWith("/bar")), RateLimitingSampler.create(10))
 *   .build());
 * }</pre>
 *
 * <p>Ex. Here's a custom matcher for the endpoint "/play&country=US"
 *
 * <p><pre>{@code
 * Matcher<HttpRequest> playInTheUSA = request -> {
 *   if (!"/play".equals(request.path())) return false;
 *   String url = request.url();
 *   if (url == null) return false;
 *   String query = URI.create(url).getQuery();
 *   return query != null && query.contains("country=US");
 * };
 * }</pre>
 *
 * <h3>Implementation notes</h3>
 * Be careful when implementing matchers as {@link HttpRequest} methods can return null.
 *
 * @since 4.4
 */
public final class HttpRuleSampler implements SamplerFunction<HttpRequest> {
  /** @since 4.4 */
  public static Builder newBuilder() {
    return new Builder();
  }

  /** @since 4.4 */
  public static final class Builder {
    final ParameterizedSampler.Builder<HttpRequest> delegate = ParameterizedSampler.newBuilder();

    /**
     * Adds or replaces all rules in this sampler with those of the input.
     *
     * @since 5.8
     */
    public Builder putAllRules(HttpRuleSampler sampler) {
      if (sampler == null) throw new NullPointerException("sampler == null");
      delegate.putAllRules(sampler.delegate);
      return this;
    }

    /**
     * Adds or replaces the sampler for the matcher.
     *
     * <p>Ex.
     * <pre>{@code
     * import static brave.http.HttpRequestMatchers.pathStartsWith;
     *
     * builder.putRule(pathStartsWith("/api"), RateLimitingSampler.create(10));
     * }</pre>
     *
     * @since 5.8
     */
    public Builder putRule(Matcher<HttpRequest> matcher, Sampler sampler) {
      delegate.putRule(matcher, sampler);
      return this;
    }

    public HttpRuleSampler build() {
      return new HttpRuleSampler(delegate.build());
    }

    Builder() {
    }
  }

  final ParameterizedSampler<HttpRequest> delegate;

  HttpRuleSampler(ParameterizedSampler<HttpRequest> delegate) {
    this.delegate = delegate;
  }

  @Override public Boolean trySample(HttpRequest request) {
    return delegate.trySample(request);
  }
}
