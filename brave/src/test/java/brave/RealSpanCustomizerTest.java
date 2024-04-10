/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave;

import brave.test.TestSpanHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class RealSpanCustomizerTest {
  TestSpanHandler spans = new TestSpanHandler();
  Tracing tracing = Tracing.newBuilder().addSpanHandler(spans).build();
  Span span = tracing.tracer().newTrace();
  SpanCustomizer spanCustomizer = span.customizer();

  @AfterEach void close() {
    tracing.close();
  }

  @Test void name() {
    spanCustomizer.name("foo");
    span.flush();

    assertThat(spans.get(0).name())
      .isEqualTo("foo");
  }

  @Test void annotate() {
    spanCustomizer.annotate("foo");
    span.flush();

    assertThat(spans.get(0).containsAnnotation("foo"))
      .isTrue();
  }

  @Test void tag() {
    spanCustomizer.tag("foo", "bar");
    span.flush();

    assertThat(spans).flatExtracting(s -> s.tags().entrySet())
      .containsExactly(entry("foo", "bar"));
  }
}
