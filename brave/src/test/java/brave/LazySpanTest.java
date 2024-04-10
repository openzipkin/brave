/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave;

import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.test.TestSpanHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LazySpanTest {
  TestSpanHandler spans = new TestSpanHandler();
  Tracing tracing = Tracing.newBuilder().addSpanHandler(spans).build();

  TraceContext context = tracing.tracer().newTrace().context();
  TraceContext context2 = tracing.tracer().newTrace().context();
  TraceContext unsampledContext =
    tracing.tracer().nextSpan(TraceContextOrSamplingFlags.NOT_SAMPLED).context();
  TraceContext unsampledContext2 =
    tracing.tracer().nextSpan(TraceContextOrSamplingFlags.NOT_SAMPLED).context();

  @AfterEach void close() {
    tracing.close();
  }

  @Test void equals_sameContext() {
    Span current1, current2;
    try (Scope scope = tracing.currentTraceContext().newScope(context)) {
      current1 = tracing.tracer().currentSpan();
      current2 = tracing.tracer().currentSpan();
    }

    assertThat(current1)
      .isInstanceOf(LazySpan.class)
      .isNotSameAs(current2)
      .isEqualTo(current2);
  }

  @Test void equals_notSameContext() {
    Span current1, current2;
    try (Scope scope = tracing.currentTraceContext().newScope(context)) {
      current1 = tracing.tracer().currentSpan();
    }
    try (Scope scope = tracing.currentTraceContext().newScope(context2)) {
      current2 = tracing.tracer().currentSpan();
    }

    assertThat(current1).isNotEqualTo(current2);
  }

  @Test void equals_realSpan_sameContext() {
    Span current;
    try (Scope scope = tracing.currentTraceContext().newScope(context)) {
      current = tracing.tracer().currentSpan();
    }

    assertThat(current).isEqualTo(tracing.tracer().toSpan(context));
  }

  @Test void equals_realSpan_notSameContext() {
    Span current;
    try (Scope scope = tracing.currentTraceContext().newScope(context)) {
      current = tracing.tracer().currentSpan();
    }

    assertThat(current).isNotEqualTo(tracing.tracer().toSpan(context2));
  }

  @Test void equals_noopSpan_sameContext() {
    Span current;
    try (Scope scope = tracing.currentTraceContext().newScope(unsampledContext)) {
      current = tracing.tracer().currentSpan();
    }

    assertThat(current).isEqualTo(tracing.tracer().toSpan(unsampledContext));
  }

  @Test void equals_noopSpan_notSameContext() {
    Span current;
    try (Scope scope = tracing.currentTraceContext().newScope(unsampledContext)) {
      current = tracing.tracer().currentSpan();
    }

    assertThat(current).isNotEqualTo(tracing.tracer().toSpan(unsampledContext2));
  }
}
