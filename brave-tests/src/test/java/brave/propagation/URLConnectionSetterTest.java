/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.propagation;

import brave.propagation.Propagation.Setter;
import brave.test.propagation.PropagationSetterTest;
import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;

/** Example setter test */
class URLConnectionSetterTest extends PropagationSetterTest<URLConnection> {
  final URLConnection request;

  public URLConnectionSetterTest() throws IOException {
    request = new URLConnection(URI.create("http://127.0.0.1:9999").toURL()) {
      @Override public void connect() {
      }
    };
  }

  @Override protected URLConnection request() {
    return request;
  }

  @Override protected Setter<URLConnection, String> setter() {
    return URLConnection::setRequestProperty;
  }

  @Override protected Iterable<String> read(URLConnection request, String key) {
    return request.getRequestProperties().get(key);
  }
}
