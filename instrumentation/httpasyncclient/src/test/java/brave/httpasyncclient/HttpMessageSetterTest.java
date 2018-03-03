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
