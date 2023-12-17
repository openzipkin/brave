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
package brave.jakarta.servlet;

import brave.Span;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class HttpServletRequestWrapperTest {
  HttpServletRequest request = mock(HttpServletRequest.class);
  HttpServletRequestWrapper wrapper =
    (HttpServletRequestWrapper) HttpServletRequestWrapper.create(request);
  Span span = mock(Span.class);

  @Test void unwrap() {
    assertThat(wrapper.unwrap())
      .isEqualTo(request);
  }

  @Test void method() {
    when(request.getMethod()).thenReturn("POST");

    assertThat(wrapper.method())
      .isEqualTo("POST");
  }

  @Test void path_doesntCrashOnNullUrl() {
    assertThat(wrapper.path())
      .isNull();
  }

  @Test void path_getRequestURI() {
    when(request.getRequestURI()).thenReturn("/bar");

    assertThat(wrapper.path())
      .isEqualTo("/bar");
  }

  @Test void url_derivedFromUrlAndQueryString() {
    when(request.getRequestURL()).thenReturn(new StringBuffer("http://foo:8080/bar"));
    when(request.getQueryString()).thenReturn("hello=world");

    assertThat(wrapper.url())
      .isEqualTo("http://foo:8080/bar?hello=world");
  }

  @Test void parseClientIpAndPort_prefersXForwardedFor() {
    when(span.remoteIpAndPort("1.2.3.4", 0)).thenReturn(true);
    when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");

    wrapper.parseClientIpAndPort(span);

    verify(span).remoteIpAndPort("1.2.3.4", 0);
    verifyNoMoreInteractions(span);
  }

  @Test void parseClientIpAndPort_skipsRemotePortOnXForwardedFor() {
    when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");
    when(span.remoteIpAndPort("1.2.3.4", 0)).thenReturn(true);

    wrapper.parseClientIpAndPort(span);

    verify(span).remoteIpAndPort("1.2.3.4", 0);
    verifyNoMoreInteractions(span);
  }

  @Test void parseClientIpAndPort_acceptsRemoteAddr() {
    when(request.getRemoteAddr()).thenReturn("1.2.3.4");
    when(request.getRemotePort()).thenReturn(61687);

    wrapper.parseClientIpAndPort(span);

    verify(span).remoteIpAndPort("1.2.3.4", 61687);
    verifyNoMoreInteractions(span);
  }

  @Test void maybeError_fromRequestAttribute() {
    Exception requestError = new Exception();
    when(request.getAttribute("error")).thenReturn(requestError);

    assertThat(wrapper.maybeError()).isSameAs(requestError);
  }

  @Test void maybeError_badRequestAttribute() {
    when(request.getAttribute("error")).thenReturn(new Object());

    assertThat(wrapper.maybeError()).isNull();
  }

  @Test void maybeError_dispatcher() {
    Exception error = new Exception();
    when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(error);

    assertThat(wrapper.maybeError()).isSameAs(error);
  }

  @Test void maybeError_dispatcher_badAttribute() {
    when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).thenReturn(new Object());

    assertThat(wrapper.maybeError()).isNull();
  }
}
