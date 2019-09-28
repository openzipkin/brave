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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * This is an implementation of how to decide whether to trace a request using annotations on a java
 * method. It is not an implementation of aspect-oriented or otherwise declarative tracing. See the
 * test cases for this class for example implementations.
 *
 * <p>Example: A user defines an annotation, for example {@code com.myco.Traced}, and a lookup
 * function for its rate (could be simple as reading a field, or even a constant). An interceptor
 * uses this sampler on each invocation of a potentially annotated target. The result decides
 * whether a new trace should be started or not.
 *
 * <p>No runtime parameters are considered here, but that doesn't mean you can't achieve
 * parameterized sampling using this. If your method is annotated such that it only accepts a
 * fraction of requests, adding a custom {@code @Traced} annotation would apply to that subset. For
 * example, if you have a JAX-RS method, it is already qualified by method and likely path. A user
 * can add and inspect their own grouping annotation to override whatever the default rate is.
 *
 * <p>Under the scenes, a map of samplers by method is maintained. The size of this map should not
 * be a problem when it directly relates to declared methods. For example, this would be invalid if
 * annotations were created at runtime and didn't match.
 *
 * @param <M> The type that uniquely identifies this method, specifically for tracing. Most often a
 * trace annotation, but could also be a {@link java.lang.reflect.Method} or another declarative
 * reference such as {@code javax.ws.rs.container.ResourceInfo}.
 * @since 4.4
 */
public abstract class DeclarativeSampler<M> {
  /** @since 5.8 */
  public interface ProbabilityOfMethod<M> {
    /** Returns null if there's no configured sample probability of this method */
    @Nullable Float get(M method);
  }

  /** @since 5.8 */
  public interface RateOfMethod<M> {
    /** Returns null if there's no configured sample rate (in traces per second) of this method */
    @Nullable Integer get(M method);
  }

  /* @since 5.8 */
  public static <M> DeclarativeSampler<M> createWithProbability(
    ProbabilityOfMethod<M> probabilityOfMethod) {
    if (probabilityOfMethod == null) throw new NullPointerException("probabilityOfMethod == null");
    return new DeclarativeCountingSampler<>(probabilityOfMethod);
  }

  /* @since 5.8 */
  public static <M> DeclarativeSampler<M> createWithRate(RateOfMethod<M> rateOfMethod) {
    if (rateOfMethod == null) throw new NullPointerException("rateOfMethod == null");
    return new DeclarativeRateLimitingSampler<>(rateOfMethod);
  }

  // this assumes input are compared by identity as typically annotations do not override hashCode
  final ConcurrentMap<M, Sampler> methodToSamplers = new ConcurrentHashMap<>();

  /**
   * Used with {@link brave.Tracer#withSampler(Sampler)} to override the default sampling decision.
   *
   * <p>Ex:
   * <pre>{@code
   * // When there is no trace in progress, this decides using an annotation
   * Sampler decideUsingAnnotation = declarativeSampler.toSampler(traced);
   * Tracer tracer = tracing.tracer().withSampler(decideUsingAnnotation);
   *
   * // This code looks the same as if there was no declarative override
   * Span span = tracer.nextSpan().name(name).start();
   * }</pre>
   *
   * @param method input to the sampling function
   * @return false if there was no rate associated with the input
   * @since 4.4
   */
  public Sampler toSampler(M method) {
    if (method == null) throw new NullPointerException("method == null");
    return new Sampler() {
      @Override public boolean isSampled(long traceId) {
        Boolean decision = sample(method).sampled();
        return decision != null ? decision : false;
      }
    };
  }

  /**
   * Like {@link #toSampler(Object)}, except allows a fallback decision, usually from {@link
   * brave.Tracing#sampler()}, when there was no rate for an input
   *
   * <p>Ex:
   * <pre>{@code
   * // When there is no trace in progress, this decides using an annotation
   * Sampler decideUsingAnnotation = declarativeSampler.toSampler(traced, tracing.sampler());
   * Tracer tracer = tracing.tracer().withSampler(decideUsingAnnotation);
   *
   * // This code looks the same as if there was no declarative override
   * brave.Span span = tracer.nextSpan().start();
   * }</pre>
   *
   * @param method input to the sampling function
   * @param fallback when there is no rate for the input, usually {@link brave.Tracing#sampler()}
   * @since 4.19
   */
  public Sampler toSampler(M method, Sampler fallback) {
    if (method == null) throw new NullPointerException("method == null");
    if (fallback == null) throw new NullPointerException("fallback == null");
    return new Sampler() {
      @Override public boolean isSampled(long traceId) {
        Boolean decision = sample(method).sampled();
        if (decision == null) return fallback.isSampled(traceId);
        return decision;
      }
    };
  }

  public SamplingFlags sample(@Nullable M method) {
    if (method == null) return SamplingFlags.EMPTY;
    Sampler sampler = methodToSamplers.get(method);
    if (sampler == NULL_SENTINEL) return SamplingFlags.EMPTY;
    if (sampler != null) return sample(sampler);

    sampler = samplerOfMethod(method);
    if (sampler == null) {
      methodToSamplers.put(method, NULL_SENTINEL);
      return SamplingFlags.EMPTY;
    }

    Sampler previousSampler = methodToSamplers.putIfAbsent(method, sampler);
    if (previousSampler != null) sampler = previousSampler; // lost race, use the existing counter
    return sample(sampler);
  }

  private SamplingFlags sample(Sampler sampler) {
    return sampler.isSampled(0L) // counting sampler ignores the input
      ? SamplingFlags.SAMPLED
      : SamplingFlags.NOT_SAMPLED;
  }

  /** Prevents us from recomputing a method that had no configured factory */
  static final Sampler NULL_SENTINEL = new Sampler() {
    @Override public boolean isSampled(long traceId) {
      throw new AssertionError();
    }
  };

  @Nullable abstract Sampler samplerOfMethod(M method);

  static final class DeclarativeCountingSampler<M> extends DeclarativeSampler<M> {
    final ProbabilityOfMethod<M> probabilityOfMethod;

    DeclarativeCountingSampler(ProbabilityOfMethod<M> probabilityOfMethod) {
      this.probabilityOfMethod = probabilityOfMethod;
    }

    @Override Sampler samplerOfMethod(M method) {
      Float probability = probabilityOfMethod.get(method);
      if (probability == null) return null;
      return CountingSampler.create(probability);
    }

    @Override public String toString() {
      return "DeclarativeCountingSampler{" + probabilityOfMethod + "}";
    }
  }

  static final class DeclarativeRateLimitingSampler<M> extends DeclarativeSampler<M> {
    final RateOfMethod<M> rateOfMethod;

    DeclarativeRateLimitingSampler(RateOfMethod<M> rateOfMethod) {
      this.rateOfMethod = rateOfMethod;
    }

    @Override Sampler samplerOfMethod(M method) {
      Integer rate = rateOfMethod.get(method);
      if (rate == null) return null;
      return RateLimitingSampler.create(rate);
    }

    @Override public String toString() {
      return "DeclarativeRateLimitingSampler{" + rateOfMethod + "}";
    }
  }

  DeclarativeSampler() {
  }

  /**
   * @since 4.4
   * @deprecated since 5.8, use {@link #createWithProbability(ProbabilityOfMethod)}
   */
  public static <M> DeclarativeSampler<M> create(RateForMethod<M> rateForMethod) {
    return createWithProbability(rateForMethod);
  }

  /**
   * @since 4.4
   * @deprecated since 5.8, use {@link ProbabilityOfMethod}
   */
  public interface RateForMethod<M> extends ProbabilityOfMethod<M> {
  }
}
