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
package brave.http;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.SamplingFlags;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpServerHandlerTest {
  List<zipkin2.Span> spans = new ArrayList<>();
  Tracer tracer;
  @Mock HttpSampler sampler;
  @Mock HttpServerAdapter<Object, Object> adapter;
  @Mock TraceContext.Extractor<Object> extractor;
  Object request = new Object();
  HttpServerHandler<Object, Object> handler;

  @Before public void init() {
    HttpTracing httpTracing = HttpTracing.newBuilder(
      Tracing.newBuilder()
        .currentTraceContext(ThreadLocalCurrentTraceContext.create())
        .spanReporter(spans::add)
        .build()
    ).serverSampler(sampler).build();
    tracer = httpTracing.tracing().tracer();
    handler = HttpServerHandler.create(httpTracing, adapter);

    when(adapter.method(request)).thenReturn("GET");
    doCallRealMethod().when(adapter).parseClientIpAndPort(eq(request), isA(brave.Span.class));
  }

  @After public void close() {
    Tracing.current().close();
  }

  @Test public void handleStart_parsesTagsWithCustomizer() {
    brave.Span span = mock(brave.Span.class);
    brave.SpanCustomizer spanCustomizer = mock(brave.SpanCustomizer.class);
    when(span.kind(Span.Kind.SERVER)).thenReturn(span);
    when(adapter.method(request)).thenReturn("GET");
    when(span.customizer()).thenReturn(spanCustomizer);

    handler.handleStart(request, span);

    verify(spanCustomizer).name("GET");
    verify(spanCustomizer).tag("http.method", "GET");
    verifyNoMoreInteractions(spanCustomizer);
  }

  @Test public void handleReceive_defaultsToMakeNewTrace() {
    when(extractor.extract(request))
      .thenReturn(TraceContextOrSamplingFlags.create(SamplingFlags.EMPTY));

    // request sampler abstains (trace ID sampler will say true)
    when(sampler.trySample(adapter, request)).thenReturn(null);

    Span newSpan = handler.handleReceive(extractor, request);
    assertThat(newSpan.isNoop()).isFalse();
    assertThat(newSpan.context().shared()).isFalse();
  }

  @Test public void handleReceive_reusesTraceId() {
    HttpTracing httpTracing = HttpTracing.create(
      Tracing.newBuilder()
        .currentTraceContext(ThreadLocalCurrentTraceContext.create())
        .supportsJoin(false)
        .spanReporter(spans::add)
        .build()
    );

    tracer = httpTracing.tracing().tracer();
    handler = HttpServerHandler.create(httpTracing, adapter);

    TraceContext incomingContext = tracer.nextSpan().context();
    when(extractor.extract(request))
      .thenReturn(TraceContextOrSamplingFlags.create(incomingContext));

    assertThat(handler.handleReceive(extractor, request).context())
      .extracting(TraceContext::traceId, TraceContext::parentId, TraceContext::shared)
      .containsOnly(incomingContext.traceId(), incomingContext.spanId(), false);
  }

  @Test public void handleReceive_reusesSpanIds() {
    TraceContext incomingContext = tracer.nextSpan().context();
    when(extractor.extract(request))
      .thenReturn(TraceContextOrSamplingFlags.create(incomingContext));

    assertThat(handler.handleReceive(extractor, request).context())
      .isEqualTo(incomingContext.toBuilder().shared(true).build());
  }

  @Test public void handleReceive_honorsSamplingFlags() {
    when(extractor.extract(request))
      .thenReturn(TraceContextOrSamplingFlags.create(SamplingFlags.NOT_SAMPLED));

    assertThat(handler.handleReceive(extractor, request).isNoop())
      .isTrue();
  }

  @Test public void handleReceive_makesRequestBasedSamplingDecision_flags() {
    when(extractor.extract(request))
      .thenReturn(TraceContextOrSamplingFlags.create(SamplingFlags.EMPTY));

    // request sampler says false eventhough trace ID sampler would have said true
    when(sampler.trySample(adapter, request)).thenReturn(false);

    assertThat(handler.handleReceive(extractor, request).isNoop())
      .isTrue();
  }

  @Test public void handleReceive_makesRequestBasedSamplingDecision_context() {
    TraceContext incomingContext = tracer.nextSpan().context().toBuilder().sampled(null).build();
    when(extractor.extract(request))
      .thenReturn(TraceContextOrSamplingFlags.create(incomingContext));

    // request sampler says false eventhough trace ID sampler would have said true
    when(sampler.trySample(adapter, request)).thenReturn(false);

    assertThat(handler.handleReceive(extractor, request).isNoop())
      .isTrue();
  }
}
