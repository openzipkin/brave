/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.sampler;

import org.junit.jupiter.api.Test;

import static brave.sampler.Matchers.alwaysMatch;
import static brave.sampler.Matchers.and;
import static brave.sampler.Matchers.neverMatch;
import static brave.sampler.Matchers.or;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class MatchersTest {
  @Test void alwaysMatch_matched() {
    assertThat(alwaysMatch().matches(null)).isTrue();
  }

  @Test void neverMatch_unmatched() {
    assertThat(neverMatch().matches(null)).isFalse();
  }

  @Test void and_empty() {
    assertThat(and()).isSameAs(neverMatch());
  }

  @Test void and_single() {
    Matcher<Boolean> one = Boolean::booleanValue;
    assertThat(and(one)).isSameAs(one);
  }

  @Test void and_multiple_matched() {
    Matcher<Void> one = b -> true;
    Matcher<Void> two = b -> true;
    Matcher<Void> three = b -> true;
    assertThat(and(one, two, three).matches(null)).isTrue();
  }

  @Test void and_multiple_unmatched() {
    Matcher<Void> one = b -> true;
    Matcher<Void> two = b -> false;
    Matcher<Void> three = b -> true;
    assertThat(and(one, two, three).matches(null)).isFalse();
  }

  @Test void or_empty() {
    assertThat(or()).isSameAs(neverMatch());
  }

  @Test void or_single() {
    Matcher<Boolean> one = Boolean::booleanValue;
    assertThat(or(one)).isSameAs(one);
  }

  @Test void or_multiple_matched() {
    Matcher<Void> one = b -> true;
    Matcher<Void> two = b -> false;
    Matcher<Void> three = b -> true;
    assertThat(or(one, two, three).matches(null)).isTrue();
  }

  @Test void or_multiple_unmatched() {
    Matcher<Void> one = b -> false;
    Matcher<Void> two = b -> false;
    Matcher<Void> three = b -> false;
    assertThat(or(one, two, three).matches(null)).isFalse();
  }

  @Test void toArray_list() {
    Matcher<Void> one = b -> true;
    Matcher<Void> two = b -> false;
    Matcher<Void> three = b -> true;
    assertThat(Matchers.toArray(asList(one, two, three)))
      .containsExactly(one, two, three);
  }

  @Test void toArray_iterable() {
    Matcher<Void> one = b -> true;
    Matcher<Void> two = b -> false;
    Matcher<Void> three = b -> true;

    assertThat(Matchers.toArray(() -> asList(one, two, three).iterator()))
      .containsExactly(one, two, three);
  }
}
