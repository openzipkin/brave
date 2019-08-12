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
package brave.features.opentracing;

import brave.Tracing;
import brave.propagation.B3Propagation;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapAdapter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import zipkin2.Annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;

/**
 * This shows how one might make an OpenTracing adapter for Brave, and how to navigate in and out of
 * the core concepts.
 */
public class OpenTracingAdapterTest {
  List<zipkin2.Span> spans = new ArrayList<>();
  Tracing brave = Tracing.newBuilder()
    .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
      .addScopeDecorator(StrictScopeDecorator.create())
      .build())
    .propagationFactory(ExtraFieldPropagation.newFactory(B3Propagation.FACTORY, "client-id"))
    .spanReporter(spans::add).build();

  BraveTracer opentracing = BraveTracer.wrap(brave);

  @After public void close() {
    brave.close();
  }

  @Test public void startWithOpenTracingAndFinishWithBrave() {
    BraveSpan openTracingSpan = opentracing.buildSpan("encode")
      .withTag("lc", "codec")
      .withStartTimestamp(1L).start();

    brave.Span braveSpan = openTracingSpan.unwrap();

    braveSpan.annotate(2L, "pump fake");
    braveSpan.finish(3L);

    checkSpanReportedToZipkin();
  }

  @Test public void startWithBraveAndFinishWithOpenTracing() {
    brave.Span braveSpan = brave.tracer().newTrace().name("encode")
      .tag("lc", "codec")
      .start(1L);

    io.opentracing.Span openTracingSpan = BraveSpan.wrap(braveSpan);

    openTracingSpan.log(2L, "pump fake");
    openTracingSpan.finish(3L);

    checkSpanReportedToZipkin();
  }

  @Test
  public void extractTraceContext() {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("X-B3-TraceId", "0000000000000001");
    map.put("X-B3-SpanId", "0000000000000002");
    map.put("X-B3-Sampled", "1");
    map.put("Client-Id", "sammy");

    BraveSpanContext openTracingContext =
      opentracing.extract(Format.Builtin.HTTP_HEADERS, new TextMapAdapter(map));

    assertThat(openTracingContext.context)
      .isEqualTo(TraceContext.newBuilder()
        .traceId(1L)
        .spanId(2L)
        .sampled(true).build());

    assertThat(openTracingContext.baggageItems())
      .containsExactly(entry("client-id", "sammy"));
  }

  @Test
  public void injectTraceContext() {
    TraceContext context = TraceContext.newBuilder()
      .traceId(1L)
      .spanId(2L)
      .sampled(true).build();

    Map<String, String> map = new LinkedHashMap<>();
    TextMapAdapter carrier = new TextMapAdapter(map);
    opentracing.inject(new BraveSpanContext(context), Format.Builtin.HTTP_HEADERS, carrier);

    assertThat(map).containsExactly(
      entry("X-B3-TraceId", "0000000000000001"),
      entry("X-B3-SpanId", "0000000000000002"),
      entry("X-B3-Sampled", "1")
    );
  }

  void checkSpanReportedToZipkin() {
    assertThat(spans).first().satisfies(s -> {
        assertThat(s.name()).isEqualTo("encode");
        assertThat(s.timestamp()).isEqualTo(1L);
        assertThat(s.annotations())
          .containsExactly(Annotation.create(2L, "pump fake"));
        assertThat(s.tags())
          .containsExactly(entry("lc", "codec"));
        assertThat(s.duration()).isEqualTo(2L);
      }
    );
  }
}
