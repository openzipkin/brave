/*
 * Copyright 2013-2020 The OpenZipkin Authors
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

public abstract class PropagationSetterTest<R> {
  protected final Propagation<String> propagation = Propagation.B3_STRING;

  protected abstract R request();

  protected abstract Propagation.Setter<R, String> setter();

  protected abstract Iterable<String> read(R request, String key);

  @Test public void set() {
    setter().put(request(), "X-B3-TraceId", "48485a3953bb6124");

    assertThat(read(request(), "X-B3-TraceId"))
      .containsExactly("48485a3953bb6124");
  }

  @Test public void set128() {
    setter().put(request(), "X-B3-TraceId", "463ac35c9f6413ad48485a3953bb6124");

    assertThat(read(request(), "X-B3-TraceId"))
      .containsExactly("463ac35c9f6413ad48485a3953bb6124");
  }

  @Test public void setTwoKeys() {
    setter().put(request(), "X-B3-TraceId", "463ac35c9f6413ad48485a3953bb6124");
    setter().put(request(), "X-B3-SpanId", "48485a3953bb6124");

    assertThat(read(request(), "X-B3-TraceId"))
      .containsExactly("463ac35c9f6413ad48485a3953bb6124");
    assertThat(read(request(), "X-B3-SpanId"))
      .containsExactly("48485a3953bb6124");
  }

  @Test public void reset() {
    setter().put(request(), "X-B3-TraceId", "48485a3953bb6124");
    setter().put(request(), "X-B3-TraceId", "463ac35c9f6413ad");

    assertThat(read(request(), "X-B3-TraceId"))
      .containsExactly("463ac35c9f6413ad");
  }
}
