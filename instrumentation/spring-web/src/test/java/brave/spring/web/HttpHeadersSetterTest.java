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
