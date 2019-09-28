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
 *   .addRuleWithRate(null, "/favicon", 0)
 *   .addRuleWithRate(null, "/foo", 100)
 *   .addRuleWithRate("POST", "/bar", 10)
 *   .build());
 * }</pre>
 *
 * <p>Note that the path is a prefix, so "/foo" will match "/foo/abcd".
 *
 * @since 4.4
 */
public final class HttpRuleSampler extends HttpSampler {
  /** @since 4.4 */
  public static Builder newBuilder() {
    return new Builder();
  }

  /** @since 4.4 */
  public static final class Builder {
    ParameterizedSampler.Builder<MethodAndPath> delegate = ParameterizedSampler.newBuilder();

    /**
     * @since 4.4
     * @deprecated Since 5.8, use {@link #addRuleWithProbability(String, String, float)}
     */
    // overload was considered and dismissed as it could result in those who used the integer 1 to
    // express 1.0, as in 100% probability, to accidentally match a rate of 1 request per second.
    @Deprecated public Builder addRule(@Nullable String method, String path, float probability) {
      return addRuleWithProbability(method, path, probability);
    }

    /**
     * Assigns a probability to all requests that match the input.
     *
     * @param method if null, any method is accepted
     * @param path all paths starting with this string are accepted
     * @param probability probability that requests that match the method and path will be sampled.
     * Expressed as a percentage. Ex 1.0 is 100%.
     */
    public Builder addRuleWithProbability(@Nullable String method, String path, float probability) {
      delegate.addRule(new MethodAndPathMatcher(method, path), CountingSampler.create(probability));
      return this;
    }

    /**
     * Assigns a sample rate to all requests that match the input.
     *
     * @param method if null, any method is accepted
     * @param path all paths starting with this string are accepted
     * @param rate max traces per second for requests that match the method and path.
     */
    public Builder addRuleWithRate(@Nullable String method, String path, int rate) {
      delegate.addRule(new MethodAndPathMatcher(method, path), RateLimitingSampler.create(rate));
      return this;
    }

    public HttpRuleSampler build() {
      return new HttpRuleSampler(delegate.build());
    }

    Builder() {
    }
  }

  final ParameterizedSampler<MethodAndPath> sampler;

  HttpRuleSampler(ParameterizedSampler<MethodAndPath> sampler) {
    this.sampler = sampler;
  }

  @Override public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request) {
    return trySample(adapter.method(request), adapter.path(request));
  }

  @Nullable Boolean trySample(@Nullable String method, @Nullable String path) {
    if (method == null || path == null) return null; // use default if we couldn't parse
    return sampler.sample(new MethodAndPath(method, path)).sampled();
  }

  static final class MethodAndPath {
    final String method;
    final String path;

    MethodAndPath(String method, String path) {
      this.method = method;
      this.path = path;
    }
  }

  static final class MethodAndPathMatcher implements ParameterizedSampler.Matcher<MethodAndPath> {
    @Nullable final String method;
    final String path;

    MethodAndPathMatcher(@Nullable String method, String path) {
      this.method = method;
      this.path = path;
    }

    @Override public boolean matches(MethodAndPath parameters) {
      if (method != null && !method.equals(parameters.method)) return false;
      return parameters.path.startsWith(path);
    }
  }
}
