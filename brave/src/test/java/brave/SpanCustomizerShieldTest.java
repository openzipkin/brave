/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpanCustomizerShieldTest {
  Tracing tracing = Tracing.newBuilder().build();

  @Test void doesNotStackOverflowOnToString() {
    Span span = tracing.tracer().newTrace();
    SpanCustomizerShield shield = new SpanCustomizerShield(span);
    assertThat(shield.toString())
      .isNotEmpty()
      .isEqualTo("SpanCustomizer(RealSpan(" + span.context().traceIdString() + "/" + span.context()
        .spanIdString() + "))");
  }
}
