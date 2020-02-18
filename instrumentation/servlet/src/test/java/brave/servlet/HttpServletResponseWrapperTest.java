/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
package brave.servlet;

import brave.http.HttpServerResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpServletResponseWrapperTest {
  HttpServletRequest request = mock(HttpServletRequest.class);
  HttpServletResponse response = mock(HttpServletResponse.class);

  @Test public void statusCode() {
    HttpServerResponse wrapper = HttpServletResponseWrapper.create(request, response, null);

    when(response.getStatus()).thenReturn(200);

    assertThat(wrapper.statusCode()).isEqualTo(200);
  }

  @Test public void statusCode_zeroNoResponse() {
    HttpServerResponse wrapper = HttpServletResponseWrapper.create(request, response, null);

    assertThat(wrapper.statusCode()).isZero();
  }

  @Test public void nullRequestOk() {
    HttpServletResponseWrapper.create(null, response, null);
  }

  @Test public void method_isRequestMethod() {
    when(request.getMethod()).thenReturn("POST");

    HttpServerResponse wrapper = HttpServletResponseWrapper.create(request, response, null);

    assertThat(wrapper.method()).isEqualTo("POST");
  }

  @Test public void route_isHttpRouteAttribute() {
    when(request.getAttribute("http.route")).thenReturn("/users/{userId}");

    HttpServerResponse wrapper = HttpServletResponseWrapper.create(request, response, null);

    assertThat(wrapper.route()).isEqualTo("/users/{userId}");
  }
}
