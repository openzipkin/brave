/*
 * Copyright 2013-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.http;

import brave.Tracing;
import brave.internal.Nullable;
import brave.sampler.CountingSampler;
import brave.sampler.Matcher;
import brave.sampler.ParameterizedSampler;
import brave.sampler.RateLimitingSampler;
import brave.sampler.Sampler;

import static brave.http.HttpRequestMatchers.methodEquals;
import static brave.http.HttpRequestMatchers.pathStartsWith;
import static brave.sampler.Matchers.and;

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
public final class HttpRuleSampler extends HttpSampler implements HttpRequestSampler {
  /** @since 4.4 */
  public static Builder newBuilder() {
    return new Builder();
  }

  /** @since 4.4 */
  public static final class Builder {
    final ParameterizedSampler.Builder<HttpRequest> delegate = ParameterizedSampler.newBuilder();

    /**
     * @since 4.4
     * @deprecated Since 5.8, use {@link #putRule(Matcher, Sampler)}
     */
    @Deprecated public Builder addRule(@Nullable String method, String path, float probability) {
      if (path == null) throw new NullPointerException("path == null");
      Sampler sampler = CountingSampler.create(probability);
      if (method == null) {
        delegate.putRule(pathStartsWith(path), RateLimitingSampler.create(10));
        return this;
      }
      delegate.putRule(and(methodEquals(method), pathStartsWith(path)), sampler);
      return this;
    }

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

  @Override public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request) {
    return trySample(new FromHttpAdapter<>(adapter, request));
  }
}
