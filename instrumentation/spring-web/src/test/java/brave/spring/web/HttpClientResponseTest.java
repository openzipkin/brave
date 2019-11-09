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
package brave.spring.web;

import brave.spring.web.TracingClientHttpRequestInterceptor.HttpClientResponse;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.client.ClientHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpClientResponseTest {
  @Mock ClientHttpResponse response;

  @Test public void statusCode() throws IOException {
    when(response.getRawStatusCode()).thenReturn(200);

    assertThat(new HttpClientResponse(response).statusCode()).isEqualTo(200);
  }

  @Test public void statusCode_zeroOnIOE() throws IOException {
    when(response.getRawStatusCode()).thenThrow(new IOException());

    assertThat(new HttpClientResponse(response).statusCode()).isZero();
  }

  @Test public void statusCode_zeroOnIAE() throws IOException {
    when(response.getRawStatusCode()).thenThrow(new IllegalArgumentException());

    assertThat(new HttpClientResponse(response).statusCode()).isZero();
  }
}
