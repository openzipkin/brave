/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static brave.http.HttpRequestMatchers.methodEquals;
import static brave.http.HttpRequestMatchers.pathStartsWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class HttpRequestMatchersTest {
  @Mock HttpRequest httpRequest;

  @Test void methodEquals_matched() {
    when(httpRequest.method()).thenReturn("GET");

    assertThat(methodEquals("GET").matches(httpRequest)).isTrue();
  }

  /** Emphasize that this is pre-baked for RFC complaint requests. */
  @Test void methodEquals_unmatched_mixedCase() {
    when(httpRequest.method()).thenReturn("PoSt");

    assertThat(methodEquals("POST").matches(httpRequest)).isFalse();
  }

  @Test void methodEquals_unmatched() {
    when(httpRequest.method()).thenReturn("POST");

    assertThat(methodEquals("GET").matches(httpRequest)).isFalse();
  }

  @Test void methodEquals_unmatched_null() {
    assertThat(methodEquals("GET").matches(httpRequest)).isFalse();
  }

  @Test void pathStartsWith_matched_exact() {
    when(httpRequest.path()).thenReturn("/foo");

    assertThat(pathStartsWith("/foo").matches(httpRequest)).isTrue();
  }

  @Test void pathStartsWith_matched_prefix() {
    when(httpRequest.path()).thenReturn("/foo/bar");

    assertThat(pathStartsWith("/foo").matches(httpRequest)).isTrue();
  }

  @Test void pathStartsWith_unmatched() {
    when(httpRequest.path()).thenReturn("/fo");

    assertThat(pathStartsWith("/foo").matches(httpRequest)).isFalse();
  }

  @Test void pathStartsWith_unmatched_null() {
    assertThat(pathStartsWith("/foo").matches(httpRequest)).isFalse();
  }
}
