/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
