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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpClientRequestTest {
  @Mock HttpClientRequest clientRequest;
  @Mock HttpClientAdapter<Object, Object> adapter;
  Object request = new Object();
  @Mock Span span;

  HttpClientRequest.ToHttpAdapter toAdapter;
  HttpClientRequest.FromHttpAdapter<Object> fromAdapter;

  @Before public void callRealMethod() {
    when(clientRequest.unwrap()).thenReturn(request);
    toAdapter = new HttpClientRequest.ToHttpAdapter(clientRequest);
    fromAdapter = new HttpClientRequest.FromHttpAdapter<>(adapter, request);
  }

  @Test public void toAdapter_startTimestamp_zeroOnNoMatch() {
    assertThat(toAdapter.startTimestamp(request)).isZero();
  }

  @Test public void toAdapter_startTimestamp_zeroOnWrongRequest() {
    assertThat(toAdapter.startTimestamp(null)).isZero();
    assertThat(toAdapter.startTimestamp(clientRequest)).isZero();
  }

  @Test public void toAdapter_startTimestamp_delegatesToClientRequest() {
    when(clientRequest.startTimestamp()).thenReturn(1L);

    assertThat(toAdapter.startTimestamp(request)).isEqualTo(1L);

    verify(clientRequest).startTimestamp();
  }

  @Test public void toAdapter_method_nullOnNoMatch() {
    assertThat(toAdapter.method(request)).isNull();
  }

  @Test public void toAdapter_method_nullOnWrongRequest() {
    assertThat(toAdapter.method(null)).isNull();
    assertThat(toAdapter.method(clientRequest)).isNull();
  }

  @Test public void toAdapter_method_delegatesToClientRequest() {
    when(clientRequest.method()).thenReturn("GET");

    assertThat(toAdapter.method(request)).isEqualTo("GET");

    verify(clientRequest).method();
  }

  @Test public void toAdapter_url_nullOnNoMatch() {
    assertThat(toAdapter.url(request)).isNull();
  }

  @Test public void toAdapter_url_nullOnWrongRequest() {
    assertThat(toAdapter.url(null)).isNull();
    assertThat(toAdapter.url(clientRequest)).isNull();
  }

  @Test public void toAdapter_url_delegatesToClientRequest() {
    when(clientRequest.url()).thenReturn("https://zipkin.io");

    assertThat(toAdapter.url(request)).isEqualTo("https://zipkin.io");

    verify(clientRequest).url();
  }

  @Test public void toAdapter_requestHeader_nullOnNoMatch() {
    assertThat(toAdapter.requestHeader(request, "Content-Type")).isNull();
  }

  @Test public void toAdapter_requestHeader_nullOnWrongRequest() {
    assertThat(toAdapter.requestHeader(null, "Content-Type")).isNull();
    assertThat(toAdapter.requestHeader(clientRequest, "Content-Type")).isNull();
  }

  @Test public void toAdapter_requestHeader_delegatesToClientRequest() {
    when(clientRequest.header("Content-Type")).thenReturn("text/plain");

    assertThat(toAdapter.requestHeader(request, "Content-Type")).isEqualTo("text/plain");

    verify(clientRequest).header("Content-Type");
  }

  @Test public void toAdapter_path_nullOnNoMatch() {
    assertThat(toAdapter.path(request)).isNull();
  }

  @Test public void toAdapter_path_nullOnWrongRequest() {
    assertThat(toAdapter.path(null)).isNull();
    assertThat(toAdapter.path(clientRequest)).isNull();
  }

  @Test public void toAdapter_path_delegatesToClientRequest() {
    when(clientRequest.path()).thenReturn("/api/v2/traces");

    assertThat(toAdapter.path(request)).isEqualTo("/api/v2/traces");

    verify(clientRequest).path();
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
