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
package brave.http;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Due to inheritance, a base type between client and server can't be used. However, we can share
// tests across the implementations, to ensure they are duplicated accurately.
@Deprecated abstract class HttpAdaptersTest<
  Req extends HttpRequest, ReqAdapter extends HttpAdapter<Object, ?>,
  Resp extends HttpResponse, RespAdapter extends HttpAdapter<?, Object>> {

  // mocks
  final Req request;
  final ReqAdapter requestAdapter;
  final Resp response;
  final RespAdapter responseAdapter;

  // types under test
  ReqAdapter toRequestAdapter;
  Req fromRequestAdapter;
  RespAdapter toResponseAdapter;
  Resp fromResponseAdapter;

  HttpAdaptersTest(
    Req request, ReqAdapter requestAdapter,
    Resp response, RespAdapter responseAdapter) {
    this.request = request;
    this.requestAdapter = requestAdapter;
    this.response = response;
    this.responseAdapter = responseAdapter;
  }

  @Test void toRequestAdapter_toString() {
    assertThat(toRequestAdapter.toString())
      .isEqualTo(request.toString());
  }

  @Test void toRequestAdapter_startTimestamp_zeroOnNoMatch() {
    assertThat(toRequestAdapter.startTimestamp(request)).isZero();
  }

  @Test void toRequestAdapter_startTimestamp_zeroOnWrongRequest() {
    assertThat(toRequestAdapter.startTimestamp(null)).isZero();
    assertThat(toRequestAdapter.startTimestamp(request)).isZero();
  }

  @Test void toRequestAdapter_startTimestamp_delegatesToHttpRequest() {
    when(request.startTimestamp()).thenReturn(1L);

    assertThat(toRequestAdapter.startTimestamp(request)).isEqualTo(1L);

    verify(request).startTimestamp();
  }

  @Test void toRequestAdapter_method_nullOnNoMatch() {
    assertThat(toRequestAdapter.method(request)).isNull();
  }

  @Test void toRequestAdapter_method_nullOnWrongRequest() {
    assertThat(toRequestAdapter.method(null)).isNull();
    assertThat(toRequestAdapter.method(request)).isNull();
  }

  @Test void toRequestAdapter_method_delegatesToHttpRequest() {
    when(request.method()).thenReturn("GET");

    assertThat(toRequestAdapter.method(request)).isEqualTo("GET");

    verify(request).method();
  }

  @Test void toRequestAdapter_url_nullOnNoMatch() {
    assertThat(toRequestAdapter.url(request)).isNull();
  }

  @Test void toRequestAdapter_url_nullOnWrongRequest() {
    assertThat(toRequestAdapter.url(null)).isNull();
    assertThat(toRequestAdapter.url(request)).isNull();
  }

  @Test void toRequestAdapter_url_delegatesToHttpRequest() {
    when(request.url()).thenReturn("https://zipkin.io");

    assertThat(toRequestAdapter.url(request)).isEqualTo("https://zipkin.io");

    verify(request).url();
  }

  @Test void toRequestAdapter_requestHeader_nullOnNoMatch() {
    assertThat(toRequestAdapter.requestHeader(request, "Content-Type")).isNull();
  }

  @Test void toRequestAdapter_requestHeader_nullOnWrongRequest() {
    assertThat(toRequestAdapter.requestHeader(null, "Content-Type")).isNull();
    assertThat(toRequestAdapter.requestHeader(request, "Content-Type")).isNull();
  }

  @Test void toRequestAdapter_requestHeader_delegatesToHttpRequest() {
    when(request.header("Content-Type")).thenReturn("text/plain");

    assertThat(toRequestAdapter.requestHeader(request, "Content-Type")).isEqualTo("text/plain");

    verify(request).header("Content-Type");
  }

  @Test void toRequestAdapter_path_nullOnNoMatch() {
    assertThat(toRequestAdapter.path(request)).isNull();
  }

  @Test void toRequestAdapter_path_nullOnWrongRequest() {
    assertThat(toRequestAdapter.path(null)).isNull();
    assertThat(toRequestAdapter.path(request)).isNull();
  }

  @Test void toRequestAdapter_path_delegatesToHttpRequest() {
    when(request.path()).thenReturn("/api/v2/traces");

    assertThat(toRequestAdapter.path(request)).isEqualTo("/api/v2/traces");

    verify(request).path();
  }

  @Test void fromRequestAdapter_unwrap() {
    assertThat(fromRequestAdapter.unwrap()).isSameAs(request);
  }

  @Test void fromRequestAdapter_toString() {
    assertThat(fromRequestAdapter.toString())
      .isEqualTo(request.toString());
  }

  @Test void fromRequestAdapter_startTimestamp_zeroOnNoMatch() {
    assertThat(fromRequestAdapter.startTimestamp()).isZero();
  }

  @Test void fromRequestAdapter_startTimestamp_delegatesToAdapter() {
    when(requestAdapter.startTimestamp(request)).thenReturn(1L);

    assertThat(fromRequestAdapter.startTimestamp()).isEqualTo(1L);

    verify(requestAdapter).startTimestamp(request);
  }

  @Test void fromRequestAdapter_method_delegatesToAdapter() {
    when(requestAdapter.method(request)).thenReturn("GET");

    assertThat(fromRequestAdapter.method()).isEqualTo("GET");

    verify(requestAdapter).method(request);
  }

  @Test void fromRequestAdapter_url_nullOnNoMatch() {
    assertThat(fromRequestAdapter.url()).isNull();
  }

  @Test void fromRequestAdapter_url_delegatesToAdapter() {
    when(requestAdapter.url(request)).thenReturn("https://zipkin.io");

    assertThat(fromRequestAdapter.url()).isEqualTo("https://zipkin.io");

    verify(requestAdapter).url(request);
  }

  @Test void fromRequestAdapter_header_nullOnNoMatch() {
    assertThat(fromRequestAdapter.header("Content-Type")).isNull();
  }

  @Test void fromRequestAdapter_header_delegatesToAdapter() {
    when(requestAdapter.requestHeader(request, "Content-Type")).thenReturn("text/plain");

    assertThat(fromRequestAdapter.header("Content-Type")).isEqualTo("text/plain");

    verify(requestAdapter).requestHeader(request, "Content-Type");
  }

  @Test void fromRequestAdapter_path_nullOnNoMatch() {
    assertThat(fromRequestAdapter.path()).isNull();
  }

  @Test void fromRequestAdapter_path_delegatesToAdapter() {
    when(requestAdapter.path(request)).thenReturn("/api/v2/traces");

    assertThat(fromRequestAdapter.path()).isEqualTo("/api/v2/traces");

    verify(requestAdapter).path(request);
  }

  ////////////

  @Test void toResponseAdapter_toString() {
    assertThat(toResponseAdapter.toString())
      .isEqualTo(response.toString());
  }

  @Test void toResponseAdapter_finishTimestamp_zeroOnNoMatch() {
    assertThat(toResponseAdapter.finishTimestamp(response)).isZero();
  }

  @Test void toResponseAdapter_finishTimestamp_zeroOnWrongResponse() {
    assertThat(toResponseAdapter.finishTimestamp(null)).isZero();
    assertThat(toResponseAdapter.finishTimestamp(response)).isZero();
  }

  @Test void toResponseAdapter_finishTimestamp_delegatesToHttpResponse() {
    when(response.finishTimestamp()).thenReturn(1L);

    assertThat(toResponseAdapter.finishTimestamp(response)).isEqualTo(1L);

    verify(response).finishTimestamp();
  }

  @Test void toResponseAdapter_methodFromResponse_nullOnNoMatch() {
    assertThat(toResponseAdapter.methodFromResponse(response)).isNull();
  }

  @Test void toResponseAdapter_methodFromResponse_nullOnWrongResponse() {
    assertThat(toResponseAdapter.methodFromResponse(null)).isNull();
    assertThat(toResponseAdapter.methodFromResponse(response)).isNull();
  }

  @Test void toResponseAdapter_methodFromResponse_delegatesToHttpResponse() {
    when(response.method()).thenReturn("GET");

    assertThat(toResponseAdapter.methodFromResponse(response)).isEqualTo("GET");

    verify(response).method();
  }

  @Test void toResponseAdapter_route_nullOnNoMatch() {
    assertThat(toResponseAdapter.route(response)).isNull();
  }

  @Test void toResponseAdapter_route_nullOnWrongResponse() {
    assertThat(toResponseAdapter.route(null)).isNull();
    assertThat(toResponseAdapter.route(response)).isNull();
  }

  @Test void toResponseAdapter_route_delegatesToHttpResponse() {
    when(response.route()).thenReturn("https://zipkin.io");

    assertThat(toResponseAdapter.route(response)).isEqualTo("https://zipkin.io");

    verify(response).route();
  }

  @Test void toResponseAdapter_statusCode_nullOnNoMatch() {
    assertThat(toResponseAdapter.statusCode(response)).isNull();
  }

  @Test void toResponseAdapter_statusCode_nullOnWrongResponse() {
    assertThat(toResponseAdapter.statusCode(null)).isNull();
    assertThat(toResponseAdapter.statusCode(response)).isNull();
  }

  @Test void toResponseAdapter_statusCode_delegatesToHttpResponse() {
    when(response.statusCode()).thenReturn(200);

    assertThat(toResponseAdapter.statusCode(response)).isEqualTo(200);
  }

  @Test void toResponseAdapter_statusCodeAsInt_zeroOnNoMatch() {
    assertThat(toResponseAdapter.statusCodeAsInt(response)).isZero();
  }

  @Test void toResponseAdapter_statusCodeAsInt_zeroOnWrongResponse() {
    assertThat(toResponseAdapter.statusCodeAsInt(null)).isZero();
    assertThat(toResponseAdapter.statusCodeAsInt(response)).isZero();
  }

  @Test void toResponseAdapter_statusCodeAsInt_delegatesToHttpResponse() {
    when(response.statusCode()).thenReturn(200);

    assertThat(toResponseAdapter.statusCodeAsInt(response)).isEqualTo(200);
  }

  @Test void fromResponseAdapter_unwrap() {
    assertThat(fromResponseAdapter.unwrap()).isSameAs(response);
  }

  @Test void fromResponseAdapter_toString() {
    assertThat(fromResponseAdapter.toString())
      .isEqualTo(response.toString());
  }

  @Test void fromResponseAdapter_finishTimestamp_zeroOnNoMatch() {
    assertThat(fromResponseAdapter.finishTimestamp()).isZero();
  }

  @Test void fromResponseAdapter_finishTimestamp_delegatesToAdapter() {
    when(responseAdapter.finishTimestamp(response)).thenReturn(1L);

    assertThat(fromResponseAdapter.finishTimestamp()).isEqualTo(1L);

    verify(responseAdapter).finishTimestamp(response);
  }

  @Test void fromResponseAdapter_methodFromResponse_nullOnNoMatch() {
    assertThat(fromResponseAdapter.method()).isNull();
  }

  @Test void fromResponseAdapter_methodFromResponse_delegatesToAdapter() {
    when(responseAdapter.methodFromResponse(response)).thenReturn("GET");

    assertThat(fromResponseAdapter.method()).isEqualTo("GET");

    verify(responseAdapter).methodFromResponse(response);
  }

  @Test void fromResponseAdapter_route_nullOnNoMatch() {
    assertThat(fromResponseAdapter.route()).isNull();
  }

  @Test void fromResponseAdapter_route_delegatesToAdapter() {
    when(responseAdapter.route(response)).thenReturn("https://zipkin.io");

    assertThat(fromResponseAdapter.route()).isEqualTo("https://zipkin.io");

    verify(responseAdapter).route(response);
  }

  @Test void fromResponseAdapter_statusCode_zeroOnNoMatch() {
    assertThat(fromResponseAdapter.statusCode()).isZero();
  }

  @Test void fromResponseAdapter_statusCode_delegatesToAdapterStatusCodeAsInt() {
    when(responseAdapter.statusCodeAsInt(response)).thenReturn(200);

    assertThat(fromResponseAdapter.statusCode()).isEqualTo(200);

    verify(responseAdapter).statusCodeAsInt(response);
  }
}
