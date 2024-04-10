/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.httpclient;

import brave.httpclient.TracingProtocolExec.HttpResponseWrapper;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.protocol.HttpClientContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class HttpResponseWrapperTest {
  @Mock HttpClientContext context;
  @Mock HttpRequest request;
  @Mock HttpResponse response;
  @Mock StatusLine statusLine;

  @Test void request() {
    when(context.getRequest()).thenReturn(request);

    assertThat(new HttpResponseWrapper(response, context, null).request().unwrap())
      .isSameAs(request);
  }

  @Test void statusCode() {
    when(response.getStatusLine()).thenReturn(statusLine);
    when(statusLine.getStatusCode()).thenReturn(200);

    assertThat(new HttpResponseWrapper(response, context, null).statusCode()).isEqualTo(200);
  }

  @Test void statusCode_zeroWhenNoStatusLine() {
    assertThat(new HttpResponseWrapper(response, context, null).statusCode()).isZero();
  }

  @Test void statusCode_zeroWhenNoResponse() {
    assertThat(new HttpResponseWrapper(null, context, new IllegalArgumentException()).statusCode())
      .isZero();
  }
}
