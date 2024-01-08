/*
 * Copyright 2013-2024 The OpenZipkin Authors
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

import brave.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // TODO: hunt down these
@Deprecated
public class HttpServerAdapterTest {
  @Mock HttpServerAdapter<Object, Object> adapter;
  @Mock Span span;
  Object request = new Object();
  Object response = new Object();

  @BeforeEach void callRealMethod() {
    doCallRealMethod().when(adapter).parseClientIpAndPort(eq(request), isA(Span.class));
    when(adapter.path(request)).thenCallRealMethod();
    when(adapter.statusCodeAsInt(response)).thenCallRealMethod();
    when(adapter.parseClientIpFromXForwardedFor(request, span)).thenCallRealMethod();
  }

  @Test void path_doesntCrashOnNullUrl() {
    assertThat(adapter.path(request))
      .isNull();
  }

  @Test void statusCodeAsInt_callsStatusCodeByDefault() {
    when(adapter.statusCode(response)).thenReturn(400);

    assertThat(adapter.statusCodeAsInt(response))
      .isEqualTo(400);
  }

  @Test void path_derivedFromUrl() {
    when(adapter.url(request)).thenReturn("http://foo:8080/bar?hello=world");

    assertThat(adapter.path(request))
      .isEqualTo("/bar");
  }

  @Test void parseClientIpAndPort_prefersXForwardedFor() {
    when(adapter.requestHeader(request, "X-Forwarded-For")).thenReturn("1.2.3.4");

    adapter.parseClientIpAndPort(request, span);

    verify(span).remoteIpAndPort("1.2.3.4", 0);
    verifyNoMoreInteractions(span);
  }

  @Test void parseClientIpAndPort_skipsOnNoIp() {
    adapter.parseClientIpAndPort(request, span);

    verifyNoMoreInteractions(span);
  }
}
