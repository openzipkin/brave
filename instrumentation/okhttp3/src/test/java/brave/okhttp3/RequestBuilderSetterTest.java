package brave.okhttp3;

import brave.propagation.Propagation;
import brave.test.propagation.PropagationSetterTest;
import okhttp3.Request;

import static brave.okhttp3.TracingInterceptor.SETTER;

public class RequestBuilderSetterTest extends PropagationSetterTest<Request.Builder, String> {
  Request.Builder carrier = new Request.Builder().url("http://foo.com");

  @Override public Propagation.KeyFactory<String> keyFactory() {
    return Propagation.KeyFactory.STRING;
  }

  @Override protected Request.Builder carrier() {
    return carrier;
  }

  @Override protected Propagation.Setter<Request.Builder, String> setter() {
    return SETTER;
  }

  @Override protected Iterable<String> read(Request.Builder carrier, String key) {
    return carrier.build().headers(key);
  }
}
