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
package brave.spring.web;

import brave.spring.web.TracingClientHttpRequestInterceptor.ClientHttpResponseWrapper;
import brave.spring.web.TracingClientHttpRequestInterceptor.HttpRequestWrapper;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.HttpClientErrorException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ClientHttpResponseWrapperTest {
  HttpRequestWrapper request = new HttpRequestWrapper(mock(HttpRequest.class));
  @Mock ClientHttpResponse response;

  @Test public void request() {
    assertThat(new ClientHttpResponseWrapper(request, response, null).request())
      .isSameAs(request);
  }

  @Test public void statusCode() throws IOException {
    when(response.getRawStatusCode()).thenReturn(200);

    assertThat(new ClientHttpResponseWrapper(request, response, null).statusCode()).isEqualTo(200);
  }

  @Test public void statusCode_zeroOnIOE() throws IOException {
    when(response.getRawStatusCode()).thenThrow(new IOException());

    assertThat(new ClientHttpResponseWrapper(request, response, null).statusCode()).isZero();
  }

  @Test public void statusCode_fromHttpStatusCodeException() {
    HttpClientErrorException ex = new HttpClientErrorException(HttpStatus.BAD_REQUEST);

    assertThat(new ClientHttpResponseWrapper(request, null, ex).statusCode()).isEqualTo(400);
  }

  @Test public void statusCode_zeroOnIAE() throws IOException {
    when(response.getRawStatusCode()).thenThrow(new IllegalArgumentException());

    assertThat(new ClientHttpResponseWrapper(request, response, null).statusCode()).isZero();
  }
}
