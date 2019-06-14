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
package brave.httpasyncclient;

import brave.propagation.Propagation;
import brave.test.propagation.PropagationSetterTest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.http.HttpMessage;
import org.apache.http.message.BasicHttpRequest;

import static brave.httpasyncclient.TracingHttpAsyncClientBuilder.SETTER;

public class HttpMessageSetterTest extends PropagationSetterTest<HttpMessage, String> {
  HttpMessage carrier = new BasicHttpRequest("GET", "/foo");

  @Override public Propagation.KeyFactory<String> keyFactory() {
    return Propagation.KeyFactory.STRING;
  }

  @Override protected HttpMessage carrier() {
    return carrier;
  }

  @Override protected Propagation.Setter<HttpMessage, String> setter() {
    return SETTER;
  }

  @Override protected Iterable<String> read(HttpMessage carrier, String key) {
    return Stream.of(carrier.getHeaders(key)).map(h -> h.getValue()).collect(Collectors.toList());
  }
}
