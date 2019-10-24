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
package brave.messaging;

import brave.Tracing;
import brave.sampler.Matcher;
import brave.sampler.ParameterizedSampler;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunction;

/**
 * Assigns sample rates to messaging requests.
 *
 * <p>Ex. Here's a sampler that traces 100 consumer requests per second, except for the "alerts"
 * channel. Other requests will use a global rate provided by the {@link Tracing tracing
 * component}.
 *
 * <p><pre>{@code
 * import brave.sampler.Matchers;
 *
 * import static brave.messaging.MessagingRequestMatchers.channelNameEquals;
 *
 * messagingTracingBuilder.consumerSampler(MessagingRuleSampler.newBuilder()
 *   .putRule(channelNameEquals("alerts"), Sampler.NEVER_SAMPLE)
 *   .putRule(Matchers.alwaysMatch(), RateLimitingSampler.create(100))
 *   .build());
 * }</pre>
 *
 * <h3>Implementation notes</h3>
 * Be careful when implementing matchers as {@link MessagingRequest} operations can return null.
 *
 * @see MessagingRequestMatchers
 * @since 5.9
 */
// Not parameterized for producer-consumer types as the generic complexity isn't worth it and properties
// specific to producer and consumer side are not used in sampling.
public final class MessagingRuleSampler implements SamplerFunction<MessagingRequest> {
  /** @since 5.9 */
  public static Builder newBuilder() {
    return new Builder();
  }

  /** @since 5.9 */
  public static final class Builder {
    final ParameterizedSampler.Builder<MessagingRequest> delegate =
      ParameterizedSampler.newBuilder();

    /**
     * Adds or replaces all rules in this sampler with those of the input.
     *
     * @since 5.9
     */
    public Builder putAllRules(MessagingRuleSampler sampler) {
      if (sampler == null) throw new NullPointerException("sampler == null");
      delegate.putAllRules(sampler.delegate);
      return this;
    }

    /**
     * Adds or replaces the sampler for the matcher.
     *
     * <p>Ex.
     * <pre>{@code
     * import static brave.messaging.MessagingRequestMatchers.operationEquals;
     *
     * builder.putRule(operationEquals("Report"), RateLimitingSampler.create(10));
     * }</pre>
     *
     * @since 5.9
     */
    public Builder putRule(Matcher matcher, Sampler sampler) {
      delegate.putRule(matcher, sampler);
      return this;
    }

    public MessagingRuleSampler build() {
      return new MessagingRuleSampler(delegate.build());
    }

    Builder() {
    }
  }

  final ParameterizedSampler<MessagingRequest> delegate;

  MessagingRuleSampler(ParameterizedSampler delegate) {
    this.delegate = delegate;
  }

  @Override public Boolean trySample(MessagingRequest request) {
    return delegate.trySample(request);
  }
}
