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
import brave.sampler.ParameterizedSampler;
import java.util.ArrayList;
import java.util.List;

/**
 * Assigns sample rates to http routes.
 *
 * <p>Ex. Here's a sampler that traces 80% requests to /foo and 10% of POST requests to /bar. Other
 * requests will use a global rate provided by the {@link Tracing tracing component}.
 * <pre>{@code
 * httpTracingBuilder.serverSampler(HttpRuleSampler.newBuilder()
 *   .addRule(null, "/foo", 0.8f)
 *   .addRule("POST", "/bar", 0.1f)
 *   .build());
 * }</pre>
 *
 * <p>Note that the path is a prefix, so "/foo" will match "/foo/abcd".
 */
public final class HttpRuleSampler extends HttpSampler {

  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder {
    final List<MethodAndPathRule> rules = new ArrayList<>();

    /**
     * Assigns a sample rate to all requests that match the input.
     *
     * @param method if null, any method is accepted
     * @param path all paths starting with this string are accepted
     * @param rate percentage of requests to start traces for. 1.0 is 100%
     */
    public Builder addRule(@Nullable String method, String path, float rate) {
      rules.add(new MethodAndPathRule(method, path, rate));
      return this;
    }

    public HttpSampler build() {
      return new HttpRuleSampler(rules);
    }

    Builder() {
    }
  }

  final ParameterizedSampler<MethodAndPath> sampler;

  HttpRuleSampler(List<MethodAndPathRule> rules) {
    this.sampler = ParameterizedSampler.create(rules);
  }

  @Override public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request) {
    String method = adapter.method(request);
    String path = adapter.path(request);
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

    @Override public boolean equals(Object o) {
      if (o == this) return true;
      if (!(o instanceof MethodAndPath)) return false;
      MethodAndPath that = (MethodAndPath) o;
      return method.equals(that.method) && path.equals(that.path);
    }

    @Override public int hashCode() {
      int h = 1;
      h *= 1000003;
      h ^= method.hashCode();
      h *= 1000003;
      h ^= path.hashCode();
      return h;
    }
  }

  static final class MethodAndPathRule extends ParameterizedSampler.Rule<MethodAndPath> {
    @Nullable final String method;
    final String path;

    MethodAndPathRule(@Nullable String method, String path, float rate) {
      super(rate);
      this.method = method;
      this.path = path;
    }

    @Override public boolean matches(MethodAndPath parameters) {
      if (method != null && !method.equals(parameters.method)) return false;
      return parameters.path.startsWith(path);
    }
  }
}
