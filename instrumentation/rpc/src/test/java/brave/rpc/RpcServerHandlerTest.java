/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rpc;

import brave.Span;
import brave.Tracing;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunction;
import brave.sampler.SamplerFunctions;
import brave.test.TestSpanHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
public class RpcServerHandlerTest {
  TestSpanHandler spans = new TestSpanHandler();
  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(1L).sampled(true).build();

  RpcTracing httpTracing;
  RpcServerHandler handler;

  @Mock(answer = CALLS_REAL_METHODS) RpcServerRequest request;
  @Mock(answer = CALLS_REAL_METHODS) RpcServerResponse response;

  @BeforeEach void init() {
    init(httpTracingBuilder(tracingBuilder()));
  }

  void init(RpcTracing.Builder builder) {
    close();
    httpTracing = builder.build();
    handler = RpcServerHandler.create(httpTracing);
    when(request.method()).thenReturn("Report");
  }

  RpcTracing.Builder httpTracingBuilder(Tracing.Builder tracingBuilder) {
    return RpcTracing.newBuilder(tracingBuilder.build());
  }

  Tracing.Builder tracingBuilder() {
    return Tracing.newBuilder().addSpanHandler(spans);
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

  @Test void handleReceive_samplerSeesRpcServerRequest() {
    SamplerFunction<RpcRequest> serverSampler = mock(SamplerFunction.class);
    init(httpTracingBuilder(tracingBuilder()).serverSampler(serverSampler));

    handler.handleReceive(request);

    verify(serverSampler).trySample(request);
  }

  @Test void externalTimestamps() {
    when(request.startTimestamp()).thenReturn(123000L);
    when(response.finishTimestamp()).thenReturn(124000L);

    Span span = handler.handleReceive(request);
    handler.handleSend(response, span);

    assertThat(spans.get(0).startTimestamp()).isEqualTo(123000L);
    assertThat(spans.get(0).finishTimestamp()).isEqualTo(124000L);
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
