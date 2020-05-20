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
package brave;

import brave.handler.MutableSpan;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import brave.test.TestSpanHandler;
import org.junit.After;
import org.junit.Test;
import zipkin2.Endpoint;

import static brave.Span.Kind;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class RealSpanTest {
  TestSpanHandler spans = new TestSpanHandler();
  Tracing tracing = Tracing.newBuilder().addSpanHandler(spans).build();

  TraceContext context = tracing.tracer().newTrace().context();
  TraceContext context2 = tracing.tracer().newTrace().context();

  Span span = tracing.tracer().newTrace();

  @After public void close() {
    tracing.close();
  }

  @Test public void isNotNoop() {
    assertThat(span.isNoop()).isFalse();
  }

  @Test public void hasRealContext() {
    assertThat(span.context().spanId()).isNotZero();
  }

  @Test public void hasRealCustomizer() {
    assertThat(span.customizer()).isInstanceOf(SpanCustomizerShield.class);
  }

  @Test public void start() {
    span.start();
    span.flush();

    assertThat(spans.get(0).startTimestamp())
      .isPositive();
  }

  @Test public void start_timestamp() {
    span.start(2);
    span.flush();

    assertThat(spans.get(0).startTimestamp())
      .isEqualTo(2);
  }

  @Test public void finish() {
    span.start();
    span.finish();

    assertThat(spans.get(0).finishTimestamp())
      .isPositive();
  }

  @Test public void finish_timestamp() {
    span.start(2);
    span.finish(5);

    assertThat(spans.get(0).startTimestamp())
      .isEqualTo(2);
    assertThat(spans.get(0).finishTimestamp())
      .isEqualTo(5);
  }

  @Test public void abandon() {
    span.start();
    span.abandon();

    assertThat(spans).isEmpty();
  }

  @Test public void annotate() {
    span.annotate("foo");
    span.flush();

    assertThat(spans.get(0).containsAnnotation("foo"))
      .isTrue();
  }

  @Deprecated @Test public void remoteEndpoint_nulls() {
    span.remoteEndpoint(Endpoint.newBuilder().build());
    span.flush();

    assertThat(spans.get(0).remoteServiceName()).isNull();
    assertThat(spans.get(0).remoteIp()).isNull();
    assertThat(spans.get(0).remotePort()).isZero();
  }

  @Test public void annotate_timestamp() {
    span.annotate(2, "foo");
    span.flush();

    assertThat(spans.get(0).annotations())
      .containsExactly(entry(2L, "foo"));
  }

  @Test public void tag() {
    span.tag("foo", "bar");
    span.flush();

    assertThat(spans).flatExtracting(s -> s.tags().entrySet())
      .containsExactly(entry("foo", "bar"));
  }

  @Test public void finished_client_annotation() {
    finish("cs", "cr", Kind.CLIENT);
  }

  @Test public void finished_server_annotation() {
    finish("sr", "ss", Kind.SERVER);
  }

  private void finish(String start, String end, Kind span2Kind) {
    Span span = tracing.tracer().newTrace().name("foo").start();
    span.annotate(1L, start);
    span.annotate(2L, end);

    MutableSpan span2 = spans.get(0);
    assertThat(span2.annotations()).isEmpty();
    assertThat(span2.startTimestamp()).isEqualTo(1L);
    assertThat(span2.finishTimestamp()).isEqualTo(2L);
    assertThat(span2.kind()).isEqualTo(span2Kind);
  }

  @Test public void doubleFinishDoesntDoubleReport() {
    Span span = tracing.tracer().newTrace().name("foo").start();

    span.finish();
    span.finish();

    assertThat(spans).hasSize(1);
  }

  @Test public void finishAfterAbandonDoesntReport() {
    span.start();
    span.abandon();
    span.finish();

    assertThat(spans).isEmpty();
  }

  @Test public void abandonAfterFinishDoesNothing() {
    span.start();
    span.finish();
    span.abandon();

    assertThat(spans).hasSize(1);
  }

  @Test public void error() {
    RuntimeException error = new RuntimeException("this cake is a lie");
    span.error(error);
    span.flush();

    assertThat(spans.get(0).error())
      .isSameAs(error);
    assertThat(spans.get(0).tags())
      .doesNotContainKey("error");
  }

  @Test public void equals_sameContext() {
    Span one = tracing.tracer().toSpan(context), two = tracing.tracer().toSpan(context);

    assertThat(one)
      .isInstanceOf(RealSpan.class)
      .isNotSameAs(two)
      .isEqualTo(two);
  }

  @Test public void equals_notSameContext() {
    Span one = tracing.tracer().toSpan(context), two = tracing.tracer().toSpan(context2);

    assertThat(one).isNotEqualTo(two);
  }

  @Test public void equals_lazySpan_sameContext() {
    Span current;
    try (Scope ws = tracing.currentTraceContext().newScope(context)) {
      current = tracing.tracer().currentSpan();
    }

    assertThat(tracing.tracer().toSpan(context)).isEqualTo(current);
  }

  @Test public void equals_lazySpan_notSameContext() {
    Span current;
    try (Scope ws = tracing.currentTraceContext().newScope(context2)) {
      current = tracing.tracer().currentSpan();
    }

    assertThat(tracing.tracer().toSpan(context)).isNotEqualTo(current);
  }
}
