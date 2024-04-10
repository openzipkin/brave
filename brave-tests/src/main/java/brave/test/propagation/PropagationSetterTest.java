/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.test.propagation;

import brave.propagation.Propagation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class PropagationSetterTest<R> {
  protected final Propagation<String> propagation = Propagation.B3_STRING;

  protected abstract R request();

  protected abstract Propagation.Setter<R, String> setter();

  protected abstract Iterable<String> read(R request, String key);

  @Test void set() {
    setter().put(request(), "X-B3-TraceId", "48485a3953bb6124");

    assertThat(read(request(), "X-B3-TraceId"))
      .containsExactly("48485a3953bb6124");
  }

  @Test void set128() {
    setter().put(request(), "X-B3-TraceId", "463ac35c9f6413ad48485a3953bb6124");

    assertThat(read(request(), "X-B3-TraceId"))
      .containsExactly("463ac35c9f6413ad48485a3953bb6124");
  }

  @Test void setTwoKeys() {
    setter().put(request(), "X-B3-TraceId", "463ac35c9f6413ad48485a3953bb6124");
    setter().put(request(), "X-B3-SpanId", "48485a3953bb6124");

    assertThat(read(request(), "X-B3-TraceId"))
      .containsExactly("463ac35c9f6413ad48485a3953bb6124");
    assertThat(read(request(), "X-B3-SpanId"))
      .containsExactly("48485a3953bb6124");
  }

  @Test void reset() {
    setter().put(request(), "X-B3-TraceId", "48485a3953bb6124");
    setter().put(request(), "X-B3-TraceId", "463ac35c9f6413ad");

    assertThat(read(request(), "X-B3-TraceId"))
      .containsExactly("463ac35c9f6413ad");
  }
}
