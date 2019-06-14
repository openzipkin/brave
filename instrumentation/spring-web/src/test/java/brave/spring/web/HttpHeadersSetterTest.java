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

import brave.propagation.Propagation;
import brave.test.propagation.PropagationSetterTest;
import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpHeaders;

import static brave.spring.web.TracingClientHttpRequestInterceptor.SETTER;

public class HttpHeadersSetterTest extends PropagationSetterTest<HttpHeaders, String> {
  HttpHeaders carrier = new HttpHeaders();

  @Override public Propagation.KeyFactory<String> keyFactory() {
    return Propagation.KeyFactory.STRING;
  }

  @Override protected HttpHeaders carrier() {
    return carrier;
  }

  @Override protected Propagation.Setter<HttpHeaders, String> setter() {
    return SETTER;
  }

  @Override protected Iterable<String> read(HttpHeaders carrier, String key) {
    List<String> result = carrier.get(key);
    return result != null ? result : Collections.emptyList();
  }
}
