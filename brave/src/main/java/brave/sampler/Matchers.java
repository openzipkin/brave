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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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
  public static <P> Matcher<P> and(Iterable<? extends Matcher<P>> matchers) {
    return and(toArray(matchers));
  }

  /** @since 5.8 */
  public static <P> Matcher<P> and(Matcher<P>... matchers) {
    return composite(matchers, true);
  }

  /** @since 5.8 */
  public static <P> Matcher<P> or(Iterable<? extends Matcher<P>> matchers) {
    return or(toArray(matchers));
  }

  /** @since 5.8 */
  public static <P> Matcher<P> or(Matcher<P>... matchers) {
    return composite(matchers, false);
  }

  static <P> Matcher[] toArray(Iterable<? extends Matcher<P>> matchers) {
    if (matchers == null) throw new NullPointerException("matchers == null");
    if (matchers instanceof Collection) {
      return (Matcher[]) ((Collection) matchers).toArray(new Matcher[0]);
    }
    List<Matcher<P>> result = new ArrayList<>();
    for (Matcher<P> matcher : matchers) result.add(matcher);
    return result.toArray(new Matcher[0]);
  }

  static <P> Matcher<P> composite(Matcher<P>[] matchers, boolean and) {
    if (matchers == null) throw new NullPointerException("matchers == null");
    if (matchers.length == 0) return neverMatch();
    for (int i = 0; i < matchers.length; i++) {
      if (matchers[i] == null) throw new NullPointerException("matchers[" + i + "] == null");
    }
    if (matchers.length == 1) return matchers[0];
    return and ? new And<>(matchers) : new Or<>(matchers);
  }

  static class And<P> implements Matcher<P> {
    final Matcher<P>[] matchers; // Array ensures no iterators are created at runtime

    And(Matcher<P>[] matchers) {
      this.matchers = Arrays.copyOf(matchers, matchers.length);
    }

    @Override public boolean matches(P parameters) {
      for (Matcher<P> matcher : matchers) {
        if (!matcher.matches(parameters)) return false;
      }
      return true;
    }

    @Override public String toString() {
      return "And(" + Arrays.toString(matchers) + ")";
    }
  }

  static class Or<P> implements Matcher<P> {
    final Matcher<P>[] matchers; // Array ensures no iterators are created at runtime

    Or(Matcher<P>[] matchers) {
      this.matchers = Arrays.copyOf(matchers, matchers.length);
    }

    @Override public boolean matches(P parameters) {
      for (Matcher<P> matcher : matchers) {
        if (matcher.matches(parameters)) return true;
      }
      return false;
    }

    @Override public String toString() {
      return "Or(" + Arrays.toString(matchers) + ")";
    }
  }
}
