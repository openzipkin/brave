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

/**
 * Convenience sampling functions.
 *
 * @see SamplerFunction
 * @since 5.8
 */
public final class SamplerFunctions {
  /**
   * Returns a function that returns null on null input instead of invoking the delegate with null.
   *
   * @since 5.8
   */
  public static <T> SamplerFunction<T> nullSafe(SamplerFunction<T> delegate) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    if (delegate instanceof Constants || delegate instanceof NullSafe) return delegate;
    return new NullSafe<>(delegate);
  }

  static final class NullSafe<T> implements SamplerFunction<T> {
    final SamplerFunction<T> delegate;

    NullSafe(SamplerFunction<T> delegate) {
      this.delegate = delegate;
    }

    @Override public Boolean trySample(T arg) {
      if (arg == null) return null;
      return delegate.trySample(arg);
    }

    @Override public String toString() {
      return "NullSafe(" + delegate + ")";
    }
  }

  /**
   * Ignores the argument and returns null. This is typically used to defer to the {@link
   * brave.Tracing#sampler() trace ID sampler}.
   *
   * @since 5.8
   */
  // using a method instead of exposing a constant allows this to be used for any argument type
  public static <T> SamplerFunction<T> deferDecision() {
    return (SamplerFunction<T>) Constants.DEFER_DECISION;
  }

  /**
   * Ignores the argument and returns false. This means it will never start new traces.
   *
   * <p>For example, you may wish to only capture traces if they originated from an inbound server
   * request. Such a policy would filter out client requests made during bootstrap.
   *
   * @since 5.8
   */
  // using a method instead of exposing a constant allows this to be used for any argument type
  public static <T> SamplerFunction<T> neverSample() {
    return (SamplerFunction<T>) Constants.NEVER_SAMPLE;
  }

  enum Constants implements SamplerFunction<Object> {
    DEFER_DECISION {
      @Override @Nullable public Boolean trySample(Object request) {
        return null;
      }

      @Override public String toString() {
        return "DeferDecision";
      }
    },
    NEVER_SAMPLE {
      @Override @Nullable public Boolean trySample(Object request) {
        return false;
      }

      @Override public String toString() {
        return "NeverSample";
      }
    }
  }
}
