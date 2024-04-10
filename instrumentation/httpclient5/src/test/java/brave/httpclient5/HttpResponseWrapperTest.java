/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.httpclient5;

import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class HttpResponseWrapperTest {
  @Mock HttpRequest request;
  @Mock HttpResponse response;

  @Test void request() {
    assertThat(
      new HttpResponseWrapper(response, new HttpRequestWrapper(request, null), null).request()
        .unwrap())
      .isSameAs(request);
  }

  @Test void statusCode() {
    when(response.getCode()).thenReturn(200);
    assertThat(new HttpResponseWrapper(response, new HttpRequestWrapper(request, null),
      null).statusCode()).isEqualTo(200);
  }

  @Test void statusCode_zeroWhenNoResponse() {
    assertThat(new HttpResponseWrapper(null, new HttpRequestWrapper(request, null),
      null).statusCode()).isZero();
  }
}
