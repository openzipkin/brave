/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.http;

import brave.Span;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunction;
import brave.sampler.SamplerFunctions;
import brave.test.IntegrationTestSpanHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // TODO: hunt down these
public class HttpServerHandlerTest {
  @RegisterExtension IntegrationTestSpanHandler spanHandler = new IntegrationTestSpanHandler();
  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(1L).sampled(true).build();

  HttpTracing httpTracing;
  HttpServerHandler<HttpServerRequest, HttpServerResponse> handler;

  @Mock(answer = CALLS_REAL_METHODS) HttpServerRequest request;
  @Mock(answer = CALLS_REAL_METHODS) HttpServerResponse response;

  @BeforeEach void init() {
    init(httpTracingBuilder(tracingBuilder()));
  }

  void init(HttpTracing.Builder builder) {
    close();
    httpTracing = builder.build();
    handler = HttpServerHandler.create(httpTracing);
    when(request.method()).thenReturn("GET");
  }

  HttpTracing.Builder httpTracingBuilder(Tracing.Builder tracingBuilder) {
    return HttpTracing.newBuilder(tracingBuilder.build());
  }

  Tracing.Builder tracingBuilder() {
    return Tracing.newBuilder().addSpanHandler(spanHandler);
  }

  @AfterEach void close() {
    Tracing current = Tracing.current();
    if (current != null) current.close();
  }

  @Test void handleReceive_traceIdSamplerSpecialCased() {
    Sampler sampler = mock(Sampler.class);

    init(httpTracingBuilder(tracingBuilder().sampler(sampler))
      .serverSampler(SamplerFunctions.deferDecision()));

    assertThat(handler.handleReceive(request).isNoop()).isTrue();

    verify(sampler).isSampled(anyLong());
  }

  @Test void handleReceive_neverSamplerSpecialCased() {
    Sampler sampler = mock(Sampler.class);

    init(httpTracingBuilder(tracingBuilder().sampler(sampler))
      .serverSampler(SamplerFunctions.neverSample()));

    assertThat(handler.handleReceive(request).isNoop()).isTrue();

    verifyNoMoreInteractions(sampler);
  }

  @Test void handleReceive_samplerSeesHttpServerRequest() {
    SamplerFunction<HttpRequest> serverSampler = mock(SamplerFunction.class);
    init(httpTracingBuilder(tracingBuilder()).serverSampler(serverSampler));

    handler.handleReceive(request);

    verify(serverSampler).trySample(request);
  }

  @Test void externalTimestamps() {
    when(request.startTimestamp()).thenReturn(123000L);
    when(response.finishTimestamp()).thenReturn(124000L);

    Span span = handler.handleReceive(request);
    handler.handleSend(response, span);

    MutableSpan received = spanHandler.takeRemoteSpan(Span.Kind.SERVER);
    assertThat(received.startTimestamp()).isEqualTo(123000L);
    assertThat(received.finishTimestamp()).isEqualTo(124000L);
  }

  @Test void handleSend_finishesSpanEvenIfUnwrappedNull() {
    brave.Span span = mock(brave.Span.class);
    when(span.context()).thenReturn(context);
    when(span.customizer()).thenReturn(span);

    handler.handleSend(response, span);

    verify(span).isNoop();
    verify(span).context();
    verify(span).customizer();
    verify(span).finish();
    verifyNoMoreInteractions(span);
  }

  @Test void handleSend_finishesSpanEvenIfUnwrappedNull_withError() {
    brave.Span span = mock(brave.Span.class);
    when(span.context()).thenReturn(context);
    when(span.customizer()).thenReturn(span);

    Exception error = new RuntimeException("peanuts");
    when(response.error()).thenReturn(error);

    handler.handleSend(response, span);

    verify(span).isNoop();
    verify(span).context();
    verify(span).customizer();
    verify(span).error(error);
    verify(span).finish();
    verifyNoMoreInteractions(span);
  }

  @Test void handleSend_oneOfResponseError() {
    brave.Span span = mock(brave.Span.class);

    assertThatThrownBy(() -> handler.handleSend(null, span))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("response == null");
  }
}
