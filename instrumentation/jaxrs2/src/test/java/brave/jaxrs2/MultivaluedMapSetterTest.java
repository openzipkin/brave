package brave.jaxrs2;

import brave.propagation.Propagation;
import brave.propagation.PropagationSetterTest;
import java.util.LinkedHashMap;
import java.util.List;
import javax.ws.rs.core.AbstractMultivaluedMap;
import javax.ws.rs.core.MultivaluedMap;

import static brave.jaxrs2.TracingClientFilter.SETTER;

public class MultivaluedMapSetterTest extends PropagationSetterTest<MultivaluedMap, String> {
  LinkedHashMap<String, List<String>> delegate = new LinkedHashMap<>();
  AbstractMultivaluedMap<String, String> carrier = new AbstractMultivaluedMap(delegate) {
  };

  @Override public Propagation.KeyFactory<String> keyFactory() {
    return Propagation.KeyFactory.STRING;
  }

  @Override protected MultivaluedMap carrier() {
    return carrier;
  }

  @Override protected Propagation.Setter<MultivaluedMap, String> setter() {
    return SETTER;
  }

  @Override protected Iterable<String> read(MultivaluedMap carrier, String key) {
    return delegate.get(key);
  }
}
