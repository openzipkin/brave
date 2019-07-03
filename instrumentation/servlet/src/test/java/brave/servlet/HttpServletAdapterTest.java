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
package brave.servlet;

import brave.Span;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpServletAdapterTest {
  HttpServletAdapter adapter = new HttpServletAdapter();
  @Mock HttpServletRequest request;
  @Mock HttpServletResponse response;
  @Mock Span span;

  @Test public void path_doesntCrashOnNullUrl() {
    assertThat(adapter.path(request))
      .isNull();
  }

  @Test public void path_getRequestURI() {
    when(request.getRequestURI()).thenReturn("/bar");

    assertThat(adapter.path(request))
      .isEqualTo("/bar");
  }

  @Test public void url_derivedFromUrlAndQueryString() {
    when(request.getRequestURL()).thenReturn(new StringBuffer("http://foo:8080/bar"));
    when(request.getQueryString()).thenReturn("hello=world");

    assertThat(adapter.url(request))
      .isEqualTo("http://foo:8080/bar?hello=world");
  }

  @Test public void parseClientIpAndPort_prefersXForwardedFor() {
    when(span.remoteIpAndPort("1.2.3.4", 0)).thenReturn(true);
    when(adapter.requestHeader(request, "X-Forwarded-For")).thenReturn("1.2.3.4");

    adapter.parseClientIpAndPort(request, span);

    verify(span).remoteIpAndPort("1.2.3.4", 0);
    verifyNoMoreInteractions(span);
  }

  @Test public void parseClientIpAndPort_skipsRemotePortOnXForwardedFor() {
    when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");
    when(span.remoteIpAndPort("1.2.3.4", 0)).thenReturn(true);

    adapter.parseClientIpAndPort(request, span);

    verify(span).remoteIpAndPort("1.2.3.4", 0);
    verifyNoMoreInteractions(span);
  }

  @Test public void parseClientIpAndPort_acceptsRemoteAddr() {
    when(request.getRemoteAddr()).thenReturn("1.2.3.4");
    when(request.getRemotePort()).thenReturn(61687);

    adapter.parseClientIpAndPort(request, span);

    verify(span).remoteIpAndPort("1.2.3.4", 61687);
    verifyNoMoreInteractions(span);
  }

  @Test public void statusCodeAsInt() {
    when(response.getStatus()).thenReturn(200);

    assertThat(adapter.statusCodeAsInt(response)).isEqualTo(200);
    assertThat(adapter.statusCode(response)).isEqualTo(200);
  }

  @Test public void statusCodeAsInt_zeroNoResponse() {
    assertThat(adapter.statusCodeAsInt(response)).isZero();
    assertThat(adapter.statusCode(response)).isNull();
  }
}
