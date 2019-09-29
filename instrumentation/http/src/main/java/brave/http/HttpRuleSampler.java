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
import brave.sampler.ParameterizedSampler;
import brave.sampler.RateLimitingSampler;

/**
 * Assigns sample rates to http routes.
 *
 * <p>Ex. Here's a sampler that traces 100 requests per second to /foo and 10 POST requests to /bar
 * per second. This doesn't start new traces for requests to favicon (which many browsers
 * automatically fetch). Other requests will use a global rate provided by the {@link Tracing
 * tracing component}.
 * <pre>{@code
 * httpTracingBuilder.serverSampler(HttpRuleSampler.newBuilder()
 *   .putRuleWithRate(null, "/favicon", 0)
 *   .putRuleWithRate(null, "/foo", 100)
 *   .putRuleWithRate("POST", "/bar", 10)
 *   .build());
 * }</pre>
 *
 * <p>Note that the path is a prefix, so "/foo" will match "/foo/abcd".
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
     * @deprecated Since 5.8, use {@link #putRuleWithProbability(String, String, float)}
     */
    // overload was considered and dismissed as it could result in those who used the integer 1 to
    // express 1.0, as in 100% probability, to accidentally match a rate of 1 request per second.
    @Deprecated public Builder addRule(@Nullable String method, String path, float probability) {
      return putRuleWithProbability(method, path, probability);
    }

    /**
     * Removes any rule associated with the method and path.
     *
     * @param method if null, any method is accepted
     * @param path all paths starting with this string are accepted
     */
    public Builder removeRule(@Nullable String method, String path) {
      delegate.removeRule(new HttpRequestMatcher(method, path));
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
     * Replaces any rule matching the method and path with a sample probability.
     *
     * @param method if null, any method is accepted
     * @param path all paths starting with this string are accepted
     * @param probability probability that requests that match the method and path will be sampled.
     * Expressed as a percentage. Ex 1.0 is 100%.
     */
    public Builder putRuleWithProbability(@Nullable String method, String path, float probability) {
      delegate.putRule(new HttpRequestMatcher(method, path), CountingSampler.create(probability));
      return this;
    }

    /**
     * Replaces any rule matching the method and path with a sample rate.
     *
     * @param method if null, any method is accepted
     * @param path all paths starting with this string are accepted
     * @param rate max traces per second for requests that match the method and path.
     */
    public Builder putRuleWithRate(@Nullable String method, String path, int rate) {
      delegate.putRule(new HttpRequestMatcher(method, path), RateLimitingSampler.create(rate));
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
    return delegate.sample(request).sampled();
  }

  @Override public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request) {
    return trySample(new FromHttpAdapter<>(adapter, request));
  }

  static final class HttpRequestMatcher implements ParameterizedSampler.Matcher<HttpRequest> {
    @Nullable final String method;
    final String path;

    HttpRequestMatcher(@Nullable String method, String path) {
      this.method = method;
      this.path = path;
    }

    @Override public boolean matches(HttpRequest request) {
      if (method != null && !method.equals(request.method())) return false;
      String requestPath = request.path();
      return requestPath != null && requestPath.startsWith(path);
    }

    @Override public boolean equals(Object o) {
      if (o == this) return true;
      if (!(o instanceof HttpRequestMatcher)) return false;
      HttpRequestMatcher that = (HttpRequestMatcher) o;
      return (method == null ? that.method == null : method.equals(that.method))
        && path.equals(that.path);
    }

    @Override public int hashCode() {
      int h = 1;
      h ^= (method == null) ? 0 : method.hashCode();
      h *= 1000003;
      h ^= path.hashCode();
      return h;
    }
  }
}
