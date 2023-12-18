/*
 * Copyright 2013-2023 The OpenZipkin Authors
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
import brave.http.HttpServerAdapters.FromRequestAdapter;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // TODO: hunt down these
@Deprecated public class DeprecatedHttpServerHandlerTest {
  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(1L).sampled(true).build();
  List<zipkin2.Span> spans = new ArrayList<>();
  @Mock HttpSampler sampler;
  HttpTracing httpTracing;
  HttpServerHandler<Object, Object> handler;

  @Spy HttpServerParser parser = new HttpServerParser();
  @Mock HttpServerAdapter<Object, Object> adapter;
  @Mock TraceContext.Extractor<Object> extractor;
  @Mock Object request;
  @Mock Object response;

  @BeforeEach void init() {
    httpTracing = HttpTracing.newBuilder(Tracing.newBuilder().spanReporter(spans::add).build())
      .serverSampler(sampler).serverParser(parser).build();
    handler = HttpServerHandler.create(httpTracing, adapter);

    when(adapter.method(request)).thenReturn("GET");
    doCallRealMethod().when(adapter).parseClientIpAndPort(eq(request), isA(Span.class));
  }

  @AfterEach void close() {
    Tracing.current().close();
  }

  @Test void handleReceive_defaultsToMakeNewTrace() {
    when(extractor.extract(request))
      .thenReturn(TraceContextOrSamplingFlags.create(SamplingFlags.EMPTY));

    // request sampler abstains (trace ID sampler will say true)
    when(sampler.trySample(any(FromRequestAdapter.class))).thenReturn(null);

    Span newSpan = handler.handleReceive(extractor, request);
    assertThat(newSpan.isNoop()).isFalse();
    assertThat(newSpan.context().shared()).isFalse();
  }

  @Test void handleReceive_reusesTraceId() {
    httpTracing = HttpTracing.newBuilder(
      Tracing.newBuilder().supportsJoin(false).spanReporter(spans::add).build())
      .serverSampler(sampler).serverParser(parser).build();

    Tracer tracer = httpTracing.tracing().tracer();
    handler = HttpServerHandler.create(httpTracing, adapter);

    TraceContext incomingContext = tracer.nextSpan().context();
    when(extractor.extract(request)).thenReturn(
      TraceContextOrSamplingFlags.create(incomingContext));

    assertThat(handler.handleReceive(extractor, request).context())
      .extracting(TraceContext::traceId, TraceContext::parentId, TraceContext::shared)
      .containsOnly(incomingContext.traceId(), incomingContext.spanId(), false);
  }

  @Test void handleReceive_reusesSpanIds() {
    TraceContext incomingContext = httpTracing.tracing().tracer().nextSpan().context();
    when(extractor.extract(request))
      .thenReturn(TraceContextOrSamplingFlags.create(incomingContext));

    assertThat(handler.handleReceive(extractor, request).context())
      .isEqualTo(incomingContext.toBuilder().shared(true).build());
  }

  @Test void handleReceive_honorsSamplingFlags() {
    when(extractor.extract(request))
      .thenReturn(TraceContextOrSamplingFlags.create(SamplingFlags.NOT_SAMPLED));

    assertThat(handler.handleReceive(extractor, request).isNoop())
      .isTrue();
  }

  @Test void handleReceive_makesRequestBasedSamplingDecision_flags() {
    when(extractor.extract(request))
      .thenReturn(TraceContextOrSamplingFlags.create(SamplingFlags.EMPTY));

    // request sampler says false eventhough trace ID sampler would have said true
    when(sampler.trySample(any(FromRequestAdapter.class))).thenReturn(false);

    assertThat(handler.handleReceive(extractor, request).isNoop())
      .isTrue();
  }

  @Test void handleReceive_makesRequestBasedSamplingDecision_context() {
    Tracer tracer = httpTracing.tracing().tracer();
    TraceContext incomingContext = tracer.nextSpan().context().toBuilder().sampled(null).build();
    when(extractor.extract(request))
      .thenReturn(TraceContextOrSamplingFlags.create(incomingContext));

    // request sampler says false eventhough trace ID sampler would have said true
    when(sampler.trySample(any(FromRequestAdapter.class))).thenReturn(false);

    assertThat(handler.handleReceive(extractor, request).isNoop())
      .isTrue();
  }

  @Test void handleSend() {
    Span span = mock(Span.class);
    when(span.context()).thenReturn(context);
    when(span.customizer()).thenReturn(span);

    handler.handleSend(response, null, span);

    verify(parser).response(eq(adapter), eq(response), isNull(), eq(span));
  }
}
