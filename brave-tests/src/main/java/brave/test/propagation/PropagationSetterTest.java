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
package brave.test.propagation;

import brave.propagation.Propagation;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class PropagationSetterTest<C, K> {
  protected abstract Propagation.KeyFactory<K> keyFactory();

  protected abstract C carrier();

  protected abstract Propagation.Setter<C, K> setter();

  protected abstract Iterable<String> read(C carrier, K key);

  @Test public void set() throws Exception {
    K key = keyFactory().create("X-B3-TraceId");
    setter().put(carrier(), key, "48485a3953bb6124");

    assertThat(read(carrier(), key))
      .containsExactly("48485a3953bb6124");
  }

  @Test public void set128() throws Exception {
    K key = keyFactory().create("X-B3-TraceId");
    setter().put(carrier(), key, "463ac35c9f6413ad48485a3953bb6124");

    assertThat(read(carrier(), key))
      .containsExactly("463ac35c9f6413ad48485a3953bb6124");
  }

  @Test public void setTwoKeys() throws Exception {
    K key1 = keyFactory().create("X-B3-TraceId");
    K key2 = keyFactory().create("X-B3-SpanId");
    setter().put(carrier(), key1, "463ac35c9f6413ad48485a3953bb6124");
    setter().put(carrier(), key2, "48485a3953bb6124");

    assertThat(read(carrier(), key1))
      .containsExactly("463ac35c9f6413ad48485a3953bb6124");
    assertThat(read(carrier(), key2))
      .containsExactly("48485a3953bb6124");
  }

  @Test public void reset() throws Exception {
    K key = keyFactory().create("X-B3-TraceId");
    setter().put(carrier(), key, "48485a3953bb6124");
    setter().put(carrier(), key, "463ac35c9f6413ad");

    assertThat(read(carrier(), key))
      .containsExactly("463ac35c9f6413ad");
  }
}
