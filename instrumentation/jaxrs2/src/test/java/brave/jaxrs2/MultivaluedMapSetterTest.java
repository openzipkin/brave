package brave.jaxrs2;

import brave.propagation.Propagation;
import brave.test.propagation.PropagationSetterTest;
import java.util.LinkedHashMap;
import java.util.List;
import javax.ws.rs.core.AbstractMultivaluedMap;
import javax.ws.rs.core.MultivaluedMap;

import static brave.jaxrs2.TracingClientFilter.SETTER;

public class MultivaluedMapSetterTest
    extends PropagationSetterTest<MultivaluedMap<String, Object>, String> {
  LinkedHashMap<String, List<String>> delegate = new LinkedHashMap<>();
  AbstractMultivaluedMap<String, Object> carrier = new AbstractMultivaluedMap(delegate) {
  };

  @Override public Propagation.KeyFactory<String> keyFactory() {
    return Propagation.KeyFactory.STRING;
  }

  @Override protected MultivaluedMap<String, Object> carrier() {
    return carrier;
  }

  @Override protected Propagation.Setter<MultivaluedMap<String, Object>, String> setter() {
    return SETTER;
  }

  @Override protected Iterable<String> read(MultivaluedMap<String, Object> carrier, String key) {
    return delegate.get(key);
  }
}
