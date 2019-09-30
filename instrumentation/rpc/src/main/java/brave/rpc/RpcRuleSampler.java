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
package brave.rpc;

import brave.Tracing;
import brave.sampler.Matcher;
import brave.sampler.ParameterizedSampler;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunction;

/**
 * Assigns sample rates to RPC requests.
 *
 * <p>Ex. Here's a sampler that traces 100 "Report" requests per second. This doesn't start new
 * traces for requests to the scribe service. Other requests will use a global rate provided by the
 * {@link Tracing tracing component}.
 *
 * <p><pre>{@code
 * import static brave.rpc.RpcRequestMatchers.*;
 *
 * rpcTracingBuilder.serverSampler(RpcRuleSampler.newBuilder()
 *   .putRule(serviceEquals("scribe"), Sampler.NEVER_SAMPLE)
 *   .putRule(methodEquals("Report"), RateLimitingSampler.create(100))
 *   .build());
 * }</pre>
 *
 * <h3>Implementation notes</h3>
 * Be careful when implementing matchers as {@link RpcRequest} methods can return null.
 *
 * @see RpcRequestMatchers
 * @since 5.8
 */
// Not parameterized for client-server types as the generic complexity isn't worth it and properties
// specific to client and server side are not used in sampling.
public final class RpcRuleSampler implements SamplerFunction<RpcRequest> {
  /** @since 5.8 */
  public static Builder newBuilder() {
    return new Builder();
  }

  /** @since 5.8 */
  public static final class Builder {
    final ParameterizedSampler.Builder<RpcRequest> delegate = ParameterizedSampler.newBuilder();

    /**
     * Adds or replaces all rules in this sampler with those of the input.
     *
     * @since 5.8
     */
    public Builder putAllRules(RpcRuleSampler sampler) {
      if (sampler == null) throw new NullPointerException("sampler == null");
      delegate.putAllRules(sampler.delegate);
      return this;
    }

    /**
     * Adds or replaces the sampler for the matcher.
     *
     * <p>Ex.
     * <pre>{@code
     * import static brave.rpc.RpcRequestMatchers.methodEquals;
     *
     * builder.putRule(methodEquals("Report"), RateLimitingSampler.create(10));
     * }</pre>
     *
     * @since 5.8
     */
    public Builder putRule(Matcher matcher, Sampler sampler) {
      delegate.putRule(matcher, sampler);
      return this;
    }

    public RpcRuleSampler build() {
      return new RpcRuleSampler(delegate.build());
    }

    Builder() {
    }
  }

  final ParameterizedSampler<RpcRequest> delegate;

  RpcRuleSampler(ParameterizedSampler delegate) {
    this.delegate = delegate;
  }

  @Override public Boolean trySample(RpcRequest request) {
    return delegate.trySample(request);
  }
}
