/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.web;

import brave.spring.web.TracingClientHttpRequestInterceptor.HttpRequestWrapper;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpRequestWrapperTest {
  HttpRequest request = mock(HttpRequest.class);

  @Test void path() {
    when(request.getURI()).thenReturn(URI.create("http://localhost/api"));

    assertThat(new HttpRequestWrapper(request).path())
      .isEqualTo("/api");
  }

  // NOTE: While technically possible, it is not easy to make URI.getPath() return null!
  @Test void path_emptyToSlash() {
    when(request.getURI()).thenReturn(URI.create("http://localhost"));

    assertThat(new HttpRequestWrapper(request).path())
      .isEqualTo("/");
  }
}
