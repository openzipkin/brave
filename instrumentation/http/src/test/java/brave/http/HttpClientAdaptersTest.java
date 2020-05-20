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
package brave.http;

import org.junit.Before;

import static org.mockito.Mockito.mock;

@Deprecated public class HttpClientAdaptersTest extends HttpAdaptersTest<
  HttpClientRequest, HttpClientAdapter<Object, ?>,
  HttpClientResponse, HttpClientAdapter<?, Object>> {

  public HttpClientAdaptersTest() {
    super(
      mock(HttpClientRequest.class), mock(HttpClientAdapter.class),
      mock(HttpClientResponse.class), mock(HttpClientAdapter.class)
    );
  }

  @Before public void setup() {
    toRequestAdapter = new HttpClientAdapters.ToRequestAdapter(request, request);
    fromRequestAdapter = new HttpClientAdapters.FromRequestAdapter<>(requestAdapter, request);
    toResponseAdapter = new HttpClientAdapters.ToResponseAdapter(response, response);
    fromResponseAdapter =
      new HttpClientAdapters.FromResponseAdapter<>(responseAdapter, response, null);
  }
}
