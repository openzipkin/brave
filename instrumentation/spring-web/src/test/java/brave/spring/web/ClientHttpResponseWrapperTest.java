/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.web;

import brave.spring.web.TracingClientHttpRequestInterceptor.ClientHttpResponseWrapper;
import brave.spring.web.TracingClientHttpRequestInterceptor.HttpRequestWrapper;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.HttpClientErrorException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ClientHttpResponseWrapperTest {
  HttpRequestWrapper request = new HttpRequestWrapper(mock(HttpRequest.class));
  @Mock ClientHttpResponse response;

  @Test void request() {
    assertThat(new ClientHttpResponseWrapper(request, response, null).request())
      .isSameAs(request);
  }

  @Test void statusCode() throws IOException {
    when(response.getRawStatusCode()).thenReturn(200);

    assertThat(new ClientHttpResponseWrapper(request, response, null).statusCode()).isEqualTo(200);
  }

  @Test void statusCode_zeroOnIOE() throws IOException {
    when(response.getRawStatusCode()).thenThrow(new IOException());

    assertThat(new ClientHttpResponseWrapper(request, response, null).statusCode()).isZero();
  }

  @Test void statusCode_fromHttpStatusCodeException() {
    HttpClientErrorException ex = new HttpClientErrorException(HttpStatus.BAD_REQUEST);

    assertThat(new ClientHttpResponseWrapper(request, null, ex).statusCode()).isEqualTo(400);
  }

  @Test void statusCode_zeroOnIAE() throws IOException {
    when(response.getRawStatusCode()).thenThrow(new IllegalArgumentException());

    assertThat(new ClientHttpResponseWrapper(request, response, null).statusCode()).isZero();
  }
}
