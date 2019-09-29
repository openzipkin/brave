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

import java.util.Arrays;

/**
 * Convenience functions to compose matchers for {@link ParameterizedSampler}.
 *
 * @see Matcher
 * @see ParameterizedSampler
 * @since 5.8
 */
public final class Matchers {

  /** @since 5.8 */
  public static <P> Matcher<P> alwaysMatch() {
    return (Matcher<P>) Constants.ALWAYS_MATCH;
  }

  /** @since 5.8 */
  public static <P> Matcher<P> neverMatch() {
    return (Matcher<P>) Constants.NEVER_MATCH;
  }

  enum Constants implements Matcher<Object> {
    ALWAYS_MATCH {
      @Override public boolean matches(Object parameters) {
        return true;
      }

      @Override public String toString() {
        return "matchAll()";
      }
    },
    NEVER_MATCH {
      @Override public boolean matches(Object parameters) {
        return false;
      }

      @Override public String toString() {
        return "neverMatch()";
      }
    }
  }

  /** @since 5.8 */
  public static <P> Matcher<P> and(Matcher<P>... matchers) {
    return composite(matchers, true);
  }

  /** @since 5.8 */
  public static <P> Matcher<P> or(Matcher<P>... matchers) {
    return composite(matchers, false);
  }

  static <P> Matcher<P> composite(Matcher<P>[] matchers, boolean and) {
    if (matchers == null) throw new NullPointerException("matchers == null");
    if (matchers.length == 0) return neverMatch();
    for (int i = 0; i < matchers.length; i++) {
      if (matchers[i] == null) throw new NullPointerException("matchers[" + i + "] == null");
    }
    if (matchers.length == 1) return matchers[0];
    return new Composite<>(matchers, and);
  }

  static final class Composite<P> implements Matcher<P> {
    final Matcher<P>[] matchers; // Array ensures no iterators are created at runtime
    final boolean and;

    Composite(Matcher<P>[] matchers, boolean and) {
      this.matchers = Arrays.copyOf(matchers, matchers.length);
      this.and = and;
    }

    @Override public boolean matches(P parameters) {
      boolean or = false;
      for (Matcher<P> matcher : matchers) {
        boolean next = matcher.matches(parameters);
        if (!next && and) return false;
        if (next) or = true;
      }
      return or;
    }

    @Override public String toString() {
      return (and ? "And" : "Or") + "(" + Arrays.toString(matchers) + ")";
    }
  }
}
