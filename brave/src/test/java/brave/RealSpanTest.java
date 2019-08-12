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
package brave;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import zipkin2.Annotation;
import zipkin2.Endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class RealSpanTest {
  List<zipkin2.Span> spans = new ArrayList<>();
  Tracing tracing = Tracing.newBuilder().spanReporter(spans::add).build();

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

    assertThat(spans).hasSize(1).first()
      .extracting(zipkin2.Span::timestamp)
      .isNotNull();
  }

  @Test public void start_timestamp() {
    span.start(2);
    span.flush();

    assertThat(spans).hasSize(1).first()
      .extracting(zipkin2.Span::timestamp)
      .isEqualTo(2L);
  }

  @Test public void finish() {
    span.start();
    span.finish();

    assertThat(spans).hasSize(1).first()
      .extracting(zipkin2.Span::duration)
      .isNotNull();
  }

  @Test public void finish_timestamp() {
    span.start(2);
    span.finish(5);

    assertThat(spans).hasSize(1).first()
      .extracting(zipkin2.Span::duration)
      .isEqualTo(3L);
  }

  @Test public void abandon() {
    span.start();
    span.abandon();

    assertThat(spans).hasSize(0);
  }

  @Test public void annotate() {
    span.annotate("foo");
    span.flush();

    assertThat(spans).flatExtracting(zipkin2.Span::annotations)
      .extracting(Annotation::value)
      .containsExactly("foo");
  }

  @Test public void remoteEndpoint_nulls() {
    span.remoteEndpoint(Endpoint.newBuilder().build());
    span.flush();

    assertThat(spans.get(0).remoteEndpoint()).isNull();
  }

  @Test public void annotate_timestamp() {
    span.annotate(2, "foo");
    span.flush();

    assertThat(spans).flatExtracting(zipkin2.Span::annotations)
      .containsExactly(Annotation.create(2L, "foo"));
  }

  @Test public void tag() {
    span.tag("foo", "bar");
    span.flush();

    assertThat(spans).flatExtracting(s -> s.tags().entrySet())
      .containsExactly(entry("foo", "bar"));
  }

  @Test public void finished_client_annotation() {
    finish("cs", "cr", zipkin2.Span.Kind.CLIENT);
  }

  @Test public void finished_server_annotation() {
    finish("sr", "ss", zipkin2.Span.Kind.SERVER);
  }

  private void finish(String start, String end, zipkin2.Span.Kind span2Kind) {
    Span span = tracing.tracer().newTrace().name("foo").start();
    span.annotate(1L, start);
    span.annotate(2L, end);

    zipkin2.Span span2 = spans.get(0);
    assertThat(span2.annotations()).isEmpty();
    assertThat(span2.timestamp()).isEqualTo(1L);
    assertThat(span2.duration()).isEqualTo(1L);
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

    assertThat(spans).hasSize(0);
  }

  @Test public void abandonAfterFinishDoesNothing() {
    span.start();
    span.finish();
    span.abandon();

    assertThat(spans).hasSize(1);
  }

  @Test public void error() {
    span.error(new RuntimeException("this cake is a lie"));
    span.flush();

    assertThat(spans).flatExtracting(s -> s.tags().entrySet())
      .containsExactly(entry("error", "this cake is a lie"));
  }

  @Test public void error_noMessage() {
    span.error(new RuntimeException());
    span.flush();

    assertThat(spans).flatExtracting(s -> s.tags().entrySet())
      .containsOnly(entry("error", "RuntimeException"));
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

  @Test public void equals_realSpan_sameContext() {
    Span current;
    try (CurrentTraceContext.Scope ws = tracing.currentTraceContext().newScope(context)) {
      current = tracing.tracer().currentSpan();
    }

    assertThat(tracing.tracer().toSpan(context)).isEqualTo(current);
  }

  @Test public void equals_realSpan_notSameContext() {
    Span current;
    try (CurrentTraceContext.Scope ws = tracing.currentTraceContext().newScope(context2)) {
      current = tracing.tracer().currentSpan();
    }

    assertThat(tracing.tracer().toSpan(context)).isNotEqualTo(current);
  }
}
