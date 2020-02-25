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
package brave.httpasyncclient;

import brave.httpasyncclient.TracingHttpAsyncClientBuilder.HttpResponseWrapper;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.protocol.HttpClientContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpResponseWrapperTest {
  @Mock HttpClientContext context;
  @Mock HttpRequest request;
  @Mock HttpResponse response;
  @Mock StatusLine statusLine;

  @Test public void request() {
    when(context.getRequest()).thenReturn(request);

    assertThat(new HttpResponseWrapper(response, context).request().unwrap())
      .isSameAs(request);
  }

  @Test public void statusCode() {
    when(response.getStatusLine()).thenReturn(statusLine);
    when(statusLine.getStatusCode()).thenReturn(200);

    assertThat(new HttpResponseWrapper(response, context).statusCode()).isEqualTo(200);
  }

  @Test public void statusCode_zeroWhenNoStatusLine() {
    assertThat(new HttpResponseWrapper(response, context).statusCode()).isZero();
  }
}
