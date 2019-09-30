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
package brave.sampler;

import brave.internal.Nullable;
import brave.propagation.SamplingFlags;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This is an implementation of how to decide whether to trace a request using ordered rules. For
 * example, you could write rules to look at an HTTP method and path, or a RabbitMQ routing key and
 * queue name.
 *
 * <p>This looks at runtime parameters to see if they {@link Matcher#matches(Object) match} a rule.
 * If all calls to a java method should have the same sample rate, consider {@link
 * DeclarativeSampler} instead.
 *
 * @param <P> The type that encloses parameters associated with a sample rate. For example, this
 * could be a pair of http and method.
 * @see Matcher
 * @since 4.4
 */
public final class ParameterizedSampler<P> implements SamplerFunction<P> {
  /** @since 5.8 */
  public static <P> Builder<P> newBuilder() {
    return new Builder<>();
  }

  /** @since 5.8 */
  public static final class Builder<P> {
    final Map<Matcher<P>, Sampler> rules = new LinkedHashMap<>();

    /**
     * Adds or replaces all rules in this sampler with those of the input.
     *
     * @since 5.8
     */
    public Builder<P> putAllRules(ParameterizedSampler<P> sampler) {
      if (sampler == null) throw new NullPointerException("sampler == null");
      for (R<P> rule : sampler.rules) putRule(rule.matcher, rule.sampler);
      return this;
    }

    /**
     * Adds or replaces the sampler of the input matcher.
     *
     * @since 5.8
     */
    public Builder<P> putRule(Matcher<P> matcher, Sampler sampler) {
      if (matcher == null) throw new NullPointerException("matcher == null");
      if (sampler == null) throw new NullPointerException("sampler == null");
      rules.put(matcher, sampler);
      return this;
    }

    public ParameterizedSampler<P> build() {
      return new ParameterizedSampler<>(this);
    }

    Builder() {
    }
  }

  static class R<P> {
    final Matcher<P> matcher;
    final Sampler sampler;

    R(Matcher<P> matcher, Sampler sampler) {
      this.matcher = matcher;
      this.sampler = sampler;
    }
  }

  final R<P>[] rules; // array avoids Map overhead at runtime

  ParameterizedSampler(Builder<P> builder) {
    this.rules = new R[builder.rules.size()];
    int i = 0;
    for (Map.Entry<Matcher<P>, Sampler> rule : builder.rules.entrySet()) {
      rules[i++] = new R<>(rule.getKey(), rule.getValue());
    }
  }

  /**
   * {@inheritDoc}
   *
   * @since 5.8
   */
  @Override public @Nullable Boolean trySample(P parameters) {
    if (parameters == null) return null;
    for (R<P> rule : rules) {
      if (rule.matcher.matches(parameters)) {
        return rule.sampler.isSampled(0L); // counting sampler ignores the input
      }
    }
    return null;
  }

  /**
   * @since 4.4
   * @deprecated Since 5.8, use {@link #trySample(Object)}
   */
  @Deprecated public SamplingFlags sample(@Nullable P parameters) {
    return SamplingFlags.Builder.build(trySample(parameters));
  }

  /**
   * @since 4.4
   * @deprecated since 5.8, use {@link #newBuilder()}
   */
  @Deprecated public static <P> ParameterizedSampler<P> create(List<? extends Rule<P>> rules) {
    if (rules == null) throw new NullPointerException("rules == null");
    Builder<P> builder = newBuilder();
    for (Rule<P> rule : rules) {
      builder.putRule(rule.matcher, rule.sampler);
    }
    return builder.build();
  }

  /**
   * @since 4.4
   * @deprecated Since 5.8, use {@link Builder#putRule(Matcher, Sampler)}
   */
  @Deprecated public static abstract class Rule<P> extends R<P> implements Matcher<P> {
    protected Rule(float probability) {
      super(null, CountingSampler.create(probability));
    }

    @Override public abstract boolean matches(P parameters);
  }
}
