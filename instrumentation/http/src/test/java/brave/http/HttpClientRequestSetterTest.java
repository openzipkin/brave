/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.http;

import brave.propagation.Propagation.Setter;
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

  @Override protected Setter<HttpClientRequest, String> setter() {
    return SETTER;
  }

  @Override protected Iterable<String> read(HttpClientRequest request, String key) {
    String result = headers.get(key);
    return result != null ? Collections.singletonList(result) : Collections.emptyList();
  }
}
