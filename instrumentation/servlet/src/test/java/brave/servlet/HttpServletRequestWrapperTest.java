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

import brave.Span;
import brave.http.HttpServerRequest;
import javax.servlet.http.HttpServletRequest;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class HttpServletRequestWrapperTest {
  HttpServletRequest request = mock(HttpServletRequest.class);
  HttpServerRequest wrapper = HttpServletRequestWrapper.create(request);
  Span span = mock(Span.class);

  @Test public void unwrap() {
    assertThat(wrapper.unwrap())
      .isEqualTo(request);
  }

  @Test public void method() {
    when(request.getMethod()).thenReturn("POST");

    assertThat(wrapper.method())
      .isEqualTo("POST");
  }

  @Test public void path_doesntCrashOnNullUrl() {
    assertThat(wrapper.path())
      .isNull();
  }

  @Test public void path_getRequestURI() {
    when(request.getRequestURI()).thenReturn("/bar");

    assertThat(wrapper.path())
      .isEqualTo("/bar");
  }

  @Test public void url_derivedFromUrlAndQueryString() {
    when(request.getRequestURL()).thenReturn(new StringBuffer("http://foo:8080/bar"));
    when(request.getQueryString()).thenReturn("hello=world");

    assertThat(wrapper.url())
      .isEqualTo("http://foo:8080/bar?hello=world");
  }

  @Test public void parseClientIpAndPort_prefersXForwardedFor() {
    when(span.remoteIpAndPort("1.2.3.4", 0)).thenReturn(true);
    when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");

    wrapper.parseClientIpAndPort(span);

    verify(span).remoteIpAndPort("1.2.3.4", 0);
    verifyNoMoreInteractions(span);
  }

  @Test public void parseClientIpAndPort_skipsRemotePortOnXForwardedFor() {
    when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");
    when(span.remoteIpAndPort("1.2.3.4", 0)).thenReturn(true);

    wrapper.parseClientIpAndPort(span);

    verify(span).remoteIpAndPort("1.2.3.4", 0);
    verifyNoMoreInteractions(span);
  }

  @Test public void parseClientIpAndPort_acceptsRemoteAddr() {
    when(request.getRemoteAddr()).thenReturn("1.2.3.4");
    when(request.getRemotePort()).thenReturn(61687);

    wrapper.parseClientIpAndPort(span);

    verify(span).remoteIpAndPort("1.2.3.4", 61687);
    verifyNoMoreInteractions(span);
  }
}
