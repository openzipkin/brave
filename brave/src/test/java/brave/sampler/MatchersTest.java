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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static brave.sampler.Matchers.alwaysMatch;
import static brave.sampler.Matchers.and;
import static brave.sampler.Matchers.neverMatch;
import static brave.sampler.Matchers.or;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class MatchersTest {
  @Test public void alwaysMatch_matched() {
    assertThat(alwaysMatch().matches(null)).isTrue();
  }

  @Test public void neverMatch_unmatched() {
    assertThat(neverMatch().matches(null)).isFalse();
  }

  @Test public void and_empty() {
    assertThat(and()).isSameAs(neverMatch());
  }

  @Test public void and_single() {
    Matcher<Boolean> one = Boolean::booleanValue;
    assertThat(and(one)).isSameAs(one);
  }

  @Test public void and_multiple_matched() {
    Matcher<Void> one = b -> true;
    Matcher<Void> two = b -> true;
    Matcher<Void> three = b -> true;
    assertThat(and(one, two, three).matches(null)).isTrue();
  }

  @Test public void and_multiple_unmatched() {
    Matcher<Void> one = b -> true;
    Matcher<Void> two = b -> false;
    Matcher<Void> three = b -> true;
    assertThat(and(one, two, three).matches(null)).isFalse();
  }

  @Test public void or_empty() {
    assertThat(or()).isSameAs(neverMatch());
  }

  @Test public void or_single() {
    Matcher<Boolean> one = Boolean::booleanValue;
    assertThat(or(one)).isSameAs(one);
  }

  @Test public void or_multiple_matched() {
    Matcher<Void> one = b -> true;
    Matcher<Void> two = b -> false;
    Matcher<Void> three = b -> true;
    assertThat(or(one, two, three).matches(null)).isTrue();
  }

  @Test public void or_multiple_unmatched() {
    Matcher<Void> one = b -> false;
    Matcher<Void> two = b -> false;
    Matcher<Void> three = b -> false;
    assertThat(or(one, two, three).matches(null)).isFalse();
  }

  @Test public void toArray_list() {
    Matcher<Void> one = b -> true;
    Matcher<Void> two = b -> false;
    Matcher<Void> three = b -> true;
    assertThat(Matchers.toArray(asList(one, two, three)))
      .containsExactly(one, two, three);
  }

  @Test public void toArray_iterable() {
    Matcher<Void> one = b -> true;
    Matcher<Void> two = b -> false;
    Matcher<Void> three = b -> true;

    assertThat(Matchers.toArray(() -> asList(one, two, three).iterator()))
      .containsExactly(one, two, three);
  }
}
