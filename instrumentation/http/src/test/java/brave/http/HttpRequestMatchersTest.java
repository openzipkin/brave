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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static brave.http.HttpRequestMatchers.methodEquals;
import static brave.http.HttpRequestMatchers.pathStartsWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpRequestMatchersTest {
  @Mock HttpRequest httpRequest;

  @Test public void methodEquals_matched() {
    when(httpRequest.method()).thenReturn("GET");

    assertThat(methodEquals("GET").matches(httpRequest)).isTrue();
  }

  /** Emphasize that this is pre-baked for RFC complaint requests. */
  @Test public void methodEquals_unmatched_mixedCase() {
    when(httpRequest.method()).thenReturn("PoSt");

    assertThat(methodEquals("POST").matches(httpRequest)).isFalse();
  }

  @Test public void methodEquals_unmatched() {
    when(httpRequest.method()).thenReturn("POST");

    assertThat(methodEquals("GET").matches(httpRequest)).isFalse();
  }

  @Test public void methodEquals_unmatched_null() {
    assertThat(methodEquals("GET").matches(httpRequest)).isFalse();
  }

  @Test public void pathStartsWith_matched_exact() {
    when(httpRequest.path()).thenReturn("/foo");

    assertThat(pathStartsWith("/foo").matches(httpRequest)).isTrue();
  }

  @Test public void pathStartsWith_matched_prefix() {
    when(httpRequest.path()).thenReturn("/foo/bar");

    assertThat(pathStartsWith("/foo").matches(httpRequest)).isTrue();
  }

  @Test public void pathStartsWith_unmatched() {
    when(httpRequest.path()).thenReturn("/fo");

    assertThat(pathStartsWith("/foo").matches(httpRequest)).isFalse();
  }

  @Test public void pathStartsWith_unmatched_null() {
    assertThat(pathStartsWith("/foo").matches(httpRequest)).isFalse();
  }
}
