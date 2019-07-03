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

import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.client.ClientHttpResponse;

import static brave.spring.web.TracingClientHttpRequestInterceptor.HttpAdapter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpAdapterTest {
  @Mock ClientHttpResponse response;
  HttpAdapter adapter = new HttpAdapter();

  @Test public void statusCodeAsInt() throws IOException {
    when(response.getRawStatusCode()).thenReturn(200);

    assertThat(adapter.statusCodeAsInt(response)).isEqualTo(200);
    assertThat(adapter.statusCode(response)).isEqualTo(200);
  }

  @Test public void statusCodeAsInt_zeroOnIOE() throws IOException {
    when(response.getRawStatusCode()).thenThrow(new IOException());

    assertThat(adapter.statusCodeAsInt(response)).isZero();
    assertThat(adapter.statusCode(response)).isNull();
  }

  @Test public void statusCodeAsInt_zeroOnIAE() throws IOException {
    when(response.getRawStatusCode()).thenThrow(new IllegalArgumentException());

    assertThat(adapter.statusCodeAsInt(response)).isZero();
    assertThat(adapter.statusCode(response)).isNull();
  }
}
