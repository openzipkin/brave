/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rpc;

import brave.Tracing;
import brave.propagation.CurrentTraceContext.Scope;
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
public class RpcClientHandlerTest {
  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(1L).sampled(true).build();
  TestSpanHandler spans = new TestSpanHandler();

  RpcTracing httpTracing;
  RpcClientHandler handler;

  @Mock(answer = CALLS_REAL_METHODS) RpcClientRequest request;
  @Mock(answer = CALLS_REAL_METHODS) RpcClientResponse response;

  @BeforeEach void init() {
    init(httpTracingBuilder(tracingBuilder()));
    when(request.method()).thenReturn("Report");
  }

  void init(RpcTracing.Builder builder) {
    close();
    httpTracing = builder.build();
    handler = RpcClientHandler.create(httpTracing);
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

  @Test void externalTimestamps() {
    when(request.startTimestamp()).thenReturn(123000L);
    when(response.finishTimestamp()).thenReturn(124000L);

    brave.Span span = handler.handleSend(request);
    handler.handleReceive(response, span);

    assertThat(spans.get(0).startTimestamp()).isEqualTo(123000L);
    assertThat(spans.get(0).finishTimestamp()).isEqualTo(124000L);
  }

  @Test void handleSend_traceIdSamplerSpecialCased() {
    Sampler sampler = mock(Sampler.class);

    init(httpTracingBuilder(tracingBuilder().sampler(sampler))
        .clientSampler(SamplerFunctions.deferDecision()));

    assertThat(handler.handleSend(request).isNoop()).isTrue();

    verify(sampler).isSampled(anyLong());
  }

  @Test void handleSend_neverSamplerSpecialCased() {
    Sampler sampler = mock(Sampler.class);

    init(httpTracingBuilder(tracingBuilder().sampler(sampler))
        .clientSampler(SamplerFunctions.neverSample()));

    assertThat(handler.handleSend(request).isNoop()).isTrue();

    verifyNoMoreInteractions(sampler);
  }

  @Test void handleSend_samplerSeesRpcClientRequest() {
    SamplerFunction<RpcRequest> clientSampler = mock(SamplerFunction.class);
    init(httpTracingBuilder(tracingBuilder()).clientSampler(clientSampler));

    handler.handleSend(request);

    verify(clientSampler).trySample(request);
  }

  @Test void handleSendWithParent_overrideContext() {
    try (Scope scope = httpTracing.tracing.currentTraceContext().newScope(context)) {
      brave.Span span = handler.handleSendWithParent(request, null);

      // If the overwrite was successful, we have a root span.
      assertThat(span.context().parentIdAsLong()).isZero();
    }
  }

  @Test void handleSendWithParent_overrideNull() {
    try (Scope scope = httpTracing.tracing.currentTraceContext().newScope(null)) {
      brave.Span span = handler.handleSendWithParent(request, context);

      // If the overwrite was successful, we have a child span.
      assertThat(span.context().parentIdAsLong()).isEqualTo(context.spanId());
    }
  }

  @Test void handleReceive_finishesSpanEvenIfUnwrappedNull() {
    brave.Span span = mock(brave.Span.class);
    when(span.context()).thenReturn(context);
    when(span.customizer()).thenReturn(span);

    handler.handleReceive(mock(RpcClientResponse.class), span);

    verify(span).isNoop();
    verify(span).context();
    verify(span).customizer();
    verify(span).finish();
    verifyNoMoreInteractions(span);
  }

  @Test void handleReceive_finishesSpanEvenIfUnwrappedNull_withError() {
    brave.Span span = mock(brave.Span.class);
    when(span.context()).thenReturn(context);
    when(span.customizer()).thenReturn(span);

    Exception error = new RuntimeException("peanuts");
    when(response.error()).thenReturn(error);

    handler.handleReceive(response, span);

    verify(span).isNoop();
    verify(span).context();
    verify(span).customizer();
    verify(span).error(error);
    verify(span).finish();
    verifyNoMoreInteractions(span);
  }

  @Test void handleReceive_responseRequired() {
    brave.Span span = mock(brave.Span.class);

    assertThatThrownBy(() -> handler.handleReceive(null, span))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("response == null");
  }
}
