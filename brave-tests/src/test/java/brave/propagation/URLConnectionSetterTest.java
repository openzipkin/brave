package brave.propagation;

import brave.test.propagation.PropagationSetterTest;
import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;

/** Example setter test */
public class URLConnectionSetterTest extends PropagationSetterTest<URLConnection, String> {
  final URLConnection carrier;

  public URLConnectionSetterTest() throws IOException {
    carrier = new URLConnection(URI.create("http://127.0.0.1:9999").toURL()) {
      @Override public void connect() throws IOException {
      }
    };
  }

  @Override public Propagation.KeyFactory<String> keyFactory() {
    return Propagation.KeyFactory.STRING;
  }

  @Override protected URLConnection carrier() {
    return carrier;
  }

  @Override protected Propagation.Setter<URLConnection, String> setter() {
    return URLConnection::setRequestProperty;
  }

  @Override protected Iterable<String> read(URLConnection carrier, String key) {
    return carrier.getRequestProperties().get(key);
  }
}
