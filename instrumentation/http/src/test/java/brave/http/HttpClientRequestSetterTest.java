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

import brave.propagation.Propagation;
import brave.test.propagation.PropagationSetterTest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static brave.http.HttpClientRequest.SETTER;

public class HttpClientRequestSetterTest extends PropagationSetterTest<HttpClientRequest> {
  Map<String, String> headers = new LinkedHashMap<>();

  @Override protected HttpClientRequest request() {
    return new HttpClientRequest() {
      @Override public Object unwrap() {
        return null;
      }

      @Override public String method() {
        return null;
      }

      @Override public String path() {
        return null;
      }

      @Override public String url() {
        return null;
      }

      @Override public String header(String name) {
        return headers.get(name);
      }

      @Override public void header(String name, String value) {
        headers.put(name, value);
      }
    };
  }

  @Override protected Propagation.Setter<HttpClientRequest
    , String> setter() {
    return SETTER;
  }

  @Override protected Iterable<String> read(HttpClientRequest request, String key) {
    String result = headers.get(key);
    return result != null ? Collections.singletonList(result) : Collections.emptyList();
  }
}
