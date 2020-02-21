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
package brave.http;

import brave.Span;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Deprecated public class HttpServerAdaptersTest extends HttpAdaptersTest<
  HttpServerRequest, HttpServerAdapter<Object, ?>,
  HttpServerResponse, HttpServerAdapter<?, Object>> {

  public HttpServerAdaptersTest() {
    super(
      mock(HttpServerRequest.class), mock(HttpServerAdapter.class),
      mock(HttpServerResponse.class), mock(HttpServerAdapter.class)
    );
  }

  Span span = mock(Span.class);

  @Before public void setup() {
    toRequestAdapter = new HttpServerAdapters.ToRequestAdapter(request, request);
    fromRequestAdapter = new HttpServerAdapters.FromRequestAdapter<>(requestAdapter, request);
    toResponseAdapter = new HttpServerAdapters.ToResponseAdapter(response, response);
    fromResponseAdapter = new HttpServerAdapters.FromResponseAdapter<>(responseAdapter, response);
  }

  @Test public void toRequestAdapter_parseClientIpAndPort_falseOnNoMatch() {
    assertThat(toRequestAdapter.parseClientIpAndPort(request, span)).isFalse();
  }

  @Test public void toRequestAdapter_parseClientIpAndPort_falseOnWrongRequest() {
    assertThat(toRequestAdapter.parseClientIpAndPort(null, span)).isFalse();
    assertThat(toRequestAdapter.parseClientIpAndPort(request, span)).isFalse();
  }

  @Test public void toRequestAdapter_parseClientIpAndPort_prioritizesXForwardedFor() {
    when(request.header("X-Forwarded-For")).thenReturn("1.2.3.4");
    when(span.remoteIpAndPort("1.2.3.4", 0)).thenReturn(true);

    assertThat(toRequestAdapter.parseClientIpAndPort(request, span)).isTrue();
  }

  @Test public void toRequestAdapter_parseClientIpAndPort_delegatesToServerRequest() {
    when(request.parseClientIpAndPort(span)).thenReturn(true);

    assertThat(toRequestAdapter.parseClientIpAndPort(request, span)).isTrue();

    verify(request).parseClientIpAndPort(span);
  }

  @Test public void fromRequestAdapter_parseClientIpAndPort_falseOnNoMatch() {
    assertThat(fromRequestAdapter.parseClientIpAndPort(span)).isFalse();
  }

  @Test public void fromRequestAdapter_parseClientIpAndPort_delegatesToAdapter() {
    when(requestAdapter.parseClientIpAndPort(eq(request), isA(Span.class))).thenReturn(true);

    assertThat(fromRequestAdapter.parseClientIpAndPort(span)).isTrue();
  }
}
