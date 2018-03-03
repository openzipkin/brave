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
