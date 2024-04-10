/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.okhttp3;

import brave.okhttp3.TracingInterceptor.RequestWrapper;
import brave.okhttp3.TracingInterceptor.ResponseWrapper;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResponseWrapperTest {
  RequestWrapper request =
    new RequestWrapper(new Request.Builder().url("http://localhost/foo").build());
  Response.Builder responseBuilder = new Response.Builder()
    .request(request.delegate)
    .protocol(Protocol.HTTP_1_1);

  @Test void request() {
    Response response = responseBuilder.code(200).message("ok").build();

    assertThat(new ResponseWrapper(request, response, null).request())
      .isSameAs(request);
  }

  @Test void statusCode() {
    Response response = responseBuilder.code(200).message("ok").build();

    assertThat(new ResponseWrapper(request, response, null).statusCode()).isEqualTo(200);
  }

  @Test void statusCode_zero() {
    Response response = responseBuilder.code(0).message("ice cream!").build();

    assertThat(new ResponseWrapper(request, response, null).statusCode()).isZero();
    assertThat(new ResponseWrapper(request, null, null).statusCode()).isZero();
  }
}
