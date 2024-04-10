/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.netty.http;

import brave.http.HttpServerRequest;
import brave.netty.http.TracingHttpServerHandler.HttpResponseWrapper;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class HttpResponseWrapperTest {
  @Mock HttpServerRequest request;
  @Mock HttpResponse response;
  @Mock HttpResponseStatus status;

  @Test void request() {
    assertThat(new HttpResponseWrapper(request, response, null).request())
      .isSameAs(request);
  }

  @Test void statusCode() {
    when(response.status()).thenReturn(status);
    when(status.code()).thenReturn(200);

    assertThat(new HttpResponseWrapper(request, response, null).statusCode()).isEqualTo(200);
  }

  @Test void statusCode_zeroNoResponse() {
    assertThat(new HttpResponseWrapper(request, response, null).statusCode()).isZero();
  }
}
