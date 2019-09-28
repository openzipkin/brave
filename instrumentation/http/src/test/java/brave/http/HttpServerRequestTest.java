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
package brave.http;

import brave.Span;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpServerRequestTest {
  @Mock HttpServerRequest serverRequest;
  @Mock HttpServerAdapter<Object, Object> adapter;
  Object request = new Object();
  @Mock Span span;

  HttpServerRequest.ToHttpAdapter toAdapter;
  HttpServerRequest.FromHttpAdapter<Object> fromAdapter;

  @Before public void callRealMethod() {
    when(serverRequest.unwrap()).thenReturn(request);
    toAdapter = new HttpServerRequest.ToHttpAdapter(serverRequest);
    fromAdapter = new HttpServerRequest.FromHttpAdapter<>(adapter, request);
  }

  @Test public void toAdapter_parseClientIpAndPort_falseOnNoMatch() {
    assertThat(toAdapter.parseClientIpAndPort(request, span)).isFalse();
  }

  @Test public void toAdapter_parseClientIpAndPort_falseOnWrongRequest() {
    assertThat(toAdapter.parseClientIpAndPort(null, span)).isFalse();
    assertThat(toAdapter.parseClientIpAndPort(serverRequest, span)).isFalse();
  }

  @Test public void toAdapter_parseClientIpAndPort_prioritizesXForwardedFor() {
    when(serverRequest.header("X-Forwarded-For")).thenReturn("1.2.3.4");
    when(span.remoteIpAndPort("1.2.3.4", 0)).thenReturn(true);

    assertThat(toAdapter.parseClientIpAndPort(request, span)).isTrue();
  }

  @Test public void toAdapter_parseClientIpAndPort_delegatesToServerRequest() {
    when(serverRequest.parseClientIpAndPort(span)).thenReturn(true);

    assertThat(toAdapter.parseClientIpAndPort(request, span)).isTrue();

    verify(serverRequest).parseClientIpAndPort(span);
  }

  @Test public void toAdapter_startTimestamp_zeroOnNoMatch() {
    assertThat(toAdapter.startTimestamp(request)).isZero();
  }

  @Test public void toAdapter_startTimestamp_zeroOnWrongRequest() {
    assertThat(toAdapter.startTimestamp(null)).isZero();
    assertThat(toAdapter.startTimestamp(serverRequest)).isZero();
  }

  @Test public void toAdapter_startTimestamp_delegatesToServerRequest() {
    when(serverRequest.startTimestamp()).thenReturn(1L);

    assertThat(toAdapter.startTimestamp(request)).isEqualTo(1L);

    verify(serverRequest).startTimestamp();
  }

  @Test public void toAdapter_method_nullOnNoMatch() {
    assertThat(toAdapter.method(request)).isNull();
  }

  @Test public void toAdapter_method_nullOnWrongRequest() {
    assertThat(toAdapter.method(null)).isNull();
    assertThat(toAdapter.method(serverRequest)).isNull();
  }

  @Test public void toAdapter_method_delegatesToServerRequest() {
    when(serverRequest.method()).thenReturn("GET");

    assertThat(toAdapter.method(request)).isEqualTo("GET");

    verify(serverRequest).method();
  }

  @Test public void toAdapter_url_nullOnNoMatch() {
    assertThat(toAdapter.url(request)).isNull();
  }

  @Test public void toAdapter_url_nullOnWrongRequest() {
    assertThat(toAdapter.url(null)).isNull();
    assertThat(toAdapter.url(serverRequest)).isNull();
  }

  @Test public void toAdapter_url_delegatesToServerRequest() {
    when(serverRequest.url()).thenReturn("https://zipkin.io");

    assertThat(toAdapter.url(request)).isEqualTo("https://zipkin.io");

    verify(serverRequest).url();
  }

  @Test public void toAdapter_requestHeader_nullOnNoMatch() {
    assertThat(toAdapter.requestHeader(request, "Content-Type")).isNull();
  }

  @Test public void toAdapter_requestHeader_nullOnWrongRequest() {
    assertThat(toAdapter.requestHeader(null, "Content-Type")).isNull();
    assertThat(toAdapter.requestHeader(serverRequest, "Content-Type")).isNull();
  }

  @Test public void toAdapter_requestHeader_delegatesToServerRequest() {
    when(serverRequest.header("Content-Type")).thenReturn("text/plain");

    assertThat(toAdapter.requestHeader(request, "Content-Type")).isEqualTo("text/plain");

    verify(serverRequest).header("Content-Type");
  }

  @Test public void toAdapter_path_nullOnNoMatch() {
    assertThat(toAdapter.path(request)).isNull();
  }

  @Test public void toAdapter_path_nullOnWrongRequest() {
    assertThat(toAdapter.path(null)).isNull();
    assertThat(toAdapter.path(serverRequest)).isNull();
  }

  @Test public void toAdapter_path_delegatesToServerRequest() {
    when(serverRequest.path()).thenReturn("/api/v2/traces");

    assertThat(toAdapter.path(request)).isEqualTo("/api/v2/traces");

    verify(serverRequest).path();
  }

  @Test public void fromAdapter_parseClientIpAndPort_falseOnNoMatch() {
    assertThat(fromAdapter.parseClientIpAndPort(span)).isFalse();
  }

  @Test public void fromAdapter_parseClientIpAndPort_delegatesToAdapter() {
    when(adapter.parseClientIpAndPort(eq(request), isA(Span.class))).thenReturn(true);

    assertThat(fromAdapter.parseClientIpAndPort(span)).isTrue();
  }

  @Test public void fromAdapter_startTimestamp_zeroOnNoMatch() {
    assertThat(fromAdapter.startTimestamp()).isZero();
  }

  @Test public void fromAdapter_startTimestamp_delegatesToAdapter() {
    when(adapter.startTimestamp(request)).thenReturn(1L);

    assertThat(fromAdapter.startTimestamp()).isEqualTo(1L);

    verify(adapter).startTimestamp(request);
  }

  @Test public void fromAdapter_method_nullOnNoMatch() {
    assertThat(fromAdapter.method()).isNull();
  }

  @Test public void fromAdapter_method_delegatesToAdapter() {
    when(adapter.method(request)).thenReturn("GET");

    assertThat(fromAdapter.method()).isEqualTo("GET");

    verify(adapter).method(request);
  }

  @Test public void fromAdapter_url_nullOnNoMatch() {
    assertThat(fromAdapter.url()).isNull();
  }

  @Test public void fromAdapter_url_delegatesToAdapter() {
    when(adapter.url(request)).thenReturn("https://zipkin.io");

    assertThat(fromAdapter.url()).isEqualTo("https://zipkin.io");

    verify(adapter).url(request);
  }

  @Test public void fromAdapter_header_nullOnNoMatch() {
    assertThat(fromAdapter.header("Content-Type")).isNull();
  }

  @Test public void fromAdapter_header_delegatesToAdapter() {
    when(adapter.requestHeader(request, "Content-Type")).thenReturn("text/plain");

    assertThat(fromAdapter.header("Content-Type")).isEqualTo("text/plain");

    verify(adapter).requestHeader(request, "Content-Type");
  }

  @Test public void fromAdapter_path_nullOnNoMatch() {
    assertThat(fromAdapter.path()).isNull();
  }

  @Test public void fromAdapter_path_delegatesToAdapter() {
    when(adapter.path(request)).thenReturn("/api/v2/traces");

    assertThat(fromAdapter.path()).isEqualTo("/api/v2/traces");

    verify(adapter).path(request);
  }
}
