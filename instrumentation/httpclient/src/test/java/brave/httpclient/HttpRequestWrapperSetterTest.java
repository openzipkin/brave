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
package brave.httpclient;

import brave.propagation.Propagation;
import brave.test.propagation.PropagationSetterTest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.message.BasicHttpRequest;

import static brave.httpclient.TracingMainExec.SETTER;

public class HttpRequestWrapperSetterTest
  extends PropagationSetterTest<HttpRequestWrapper, String> {
  HttpRequestWrapper carrier = HttpRequestWrapper.wrap(new BasicHttpRequest("GET", "/foo"));

  @Override public Propagation.KeyFactory<String> keyFactory() {
    return Propagation.KeyFactory.STRING;
  }

  @Override protected HttpRequestWrapper carrier() {
    return carrier;
  }

  @Override protected Propagation.Setter<HttpRequestWrapper, String> setter() {
    return SETTER;
  }

  @Override protected Iterable<String> read(HttpRequestWrapper carrier, String key) {
    return Stream.of(carrier.getHeaders(key)).map(h -> h.getValue()).collect(Collectors.toList());
  }
}
