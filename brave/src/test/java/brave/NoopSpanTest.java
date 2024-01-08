/*
 * Copyright 2013-2024 The OpenZipkin Authors
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
package brave;

import brave.Tracer.SpanInScope;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoopSpanTest {
  Tracer tracer = Tracing.newBuilder().sampler(Sampler.NEVER_SAMPLE)
    .clock(() -> {
      throw new AssertionError();
    })
    .addSpanHandler(new SpanHandler() {
      @Override public boolean begin(TraceContext context, MutableSpan span, TraceContext parent) {
        throw new AssertionError();
      }
    })
    .build().tracer();
  Span span = tracer.newTrace();

  @AfterEach void close() {
    Tracing.current().close();
  }

  @Test void isNoop() {
    assertThat(span.isNoop()).isTrue();
  }

  @Test void hasRealContext() {
    assertThat(span.context().spanId()).isNotZero();
  }

  @Test void hasNoopCustomizer() {
    assertThat(span.customizer()).isSameAs(NoopSpanCustomizer.INSTANCE);
  }

  @Test void doesNothing() {
    // Since our clock and spanReporter throw, we know this is doing nothing
    span.start();
    span.start(1L);
    span.annotate("foo");
    span.annotate(2L, "foo");
    span.tag("bar", "baz");
    span.remoteServiceName("aloha");
    span.remoteIpAndPort("1.2.3.4", 9000);
    span.finish(1L);
    span.finish();
    span.abandon();
    span.flush();
  }

  @Test void equals_lazySpan_sameContext() {
    Span current;
    try (SpanInScope scope = tracer.withSpanInScope(span)) {
      current = tracer.currentSpan();
    }

    assertThat(span).isEqualTo(current);
  }

  @Test void equals_lazySpan_notSameContext() {
    Span current;
    try (SpanInScope scope = tracer.withSpanInScope(tracer.newTrace())) {
      current = tracer.currentSpan();
    }

    assertThat(span).isNotEqualTo(current);
  }
}
