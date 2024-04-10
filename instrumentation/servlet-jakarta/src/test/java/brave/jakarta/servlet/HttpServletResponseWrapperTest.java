/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jakarta.servlet;

import brave.http.HttpServerResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpServletResponseWrapperTest {
  HttpServletRequest request = mock(HttpServletRequest.class);
  HttpServletResponse response = mock(HttpServletResponse.class);

  @Test void unwrap() {
    HttpServerResponse wrapper = HttpServletResponseWrapper.create(request, response, null);

    assertThat(wrapper.unwrap())
      .isEqualTo(response);
  }

  @Test void statusCode() {
    when(response.getStatus()).thenReturn(200);

    HttpServerResponse wrapper = HttpServletResponseWrapper.create(request, response, null);

    assertThat(wrapper.statusCode()).isEqualTo(200);
  }

  @Test void statusCode_zeroNoResponse() {
    HttpServerResponse wrapper = HttpServletResponseWrapper.create(request, response, null);

    assertThat(wrapper.statusCode()).isZero();
  }

  @Test void nullRequestOk() {
    HttpServletResponseWrapper.create(null, response, null);
  }

  @Test void method_isRequestMethod() {
    HttpServerResponse wrapper = HttpServletResponseWrapper.create(request, response, null);

    when(request.getMethod()).thenReturn("POST");

    assertThat(wrapper.method()).isEqualTo("POST");
  }

  @Test void error_noRequest() {
    Exception error = new Exception();
    HttpServerResponse wrapper = HttpServletResponseWrapper.create(null, response, error);

    assertThat(wrapper.error()).isSameAs(error);
  }

  @Test void error_fromRequestAttribute() {
    HttpServerResponse wrapper = HttpServletResponseWrapper.create(request, response, null);

    Exception requestError = new Exception();
    when(request.getAttribute("error")).thenReturn(requestError);

    assertThat(wrapper.error()).isSameAs(requestError);
  }

  @Test void error_badRequestAttribute() {
    HttpServerResponse wrapper = HttpServletResponseWrapper.create(request, response, null);

    when(request.getAttribute("error")).thenReturn(new Object());

    assertThat(wrapper.error()).isNull();
  }

  @Test void error_overridesRequestAttribute() {
    Exception error = new Exception();

    HttpServerResponse wrapper = HttpServletResponseWrapper.create(request, response, error);

    Exception requestError = new Exception();
    when(request.getAttribute("error")).thenReturn(requestError);

    assertThat(wrapper.error()).isSameAs(error);
  }

  @Test void route_okOnBadAttribute() {
    HttpServerResponse wrapper = HttpServletResponseWrapper.create(request, response, null);

    when(request.getAttribute("http.route")).thenReturn(new Object());

    assertThat(wrapper.route()).isNull();
  }

  @Test void route_isHttpRouteAttribute() {
    HttpServerResponse wrapper = HttpServletResponseWrapper.create(request, response, null);

    when(request.getAttribute("http.route")).thenReturn("/users/{userId}");

    assertThat(wrapper.route()).isEqualTo("/users/{userId}");
  }
}
