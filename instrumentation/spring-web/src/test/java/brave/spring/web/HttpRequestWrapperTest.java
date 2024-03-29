/*
 * Copyright 2013-2023 The OpenZipkin Authors
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
