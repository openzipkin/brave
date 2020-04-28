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
package brave.rpc;

import brave.Tracing;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunction;
import brave.sampler.SamplerFunctions;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RpcClientHandlerTest {
  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(1L).sampled(true).build();
  List<Span> spans = new ArrayList<>();

  RpcTracing httpTracing;
  RpcClientHandler handler;

  @Mock(answer = CALLS_REAL_METHODS) RpcClientRequest request;
  @Mock(answer = CALLS_REAL_METHODS) RpcClientResponse response;

  @Before public void init() {
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
    return Tracing.newBuilder().spanReporter(spans::add);
  }

  @After public void close() {
    Tracing current = Tracing.current();
    if (current != null) current.close();
  }

  @Test public void externalTimestamps() {
    when(request.startTimestamp()).thenReturn(123000L);
    when(response.finishTimestamp()).thenReturn(124000L);

    brave.Span span = handler.handleSend(request);
    handler.handleReceive(response, span);

    assertThat(spans.get(0).durationAsLong()).isEqualTo(1000L);
  }

  @Test public void handleSend_traceIdSamplerSpecialCased() {
    Sampler sampler = mock(Sampler.class);

    init(httpTracingBuilder(tracingBuilder().sampler(sampler))
        .clientSampler(SamplerFunctions.deferDecision()));

    assertThat(handler.handleSend(request).isNoop()).isTrue();

    verify(sampler).isSampled(anyLong());
  }

  @Test public void handleSend_neverSamplerSpecialCased() {
    Sampler sampler = mock(Sampler.class);

    init(httpTracingBuilder(tracingBuilder().sampler(sampler))
        .clientSampler(SamplerFunctions.neverSample()));

    assertThat(handler.handleSend(request).isNoop()).isTrue();

    verifyNoMoreInteractions(sampler);
  }

  @Test public void handleSend_samplerSeesRpcClientRequest() {
    SamplerFunction<RpcRequest> clientSampler = mock(SamplerFunction.class);
    init(httpTracingBuilder(tracingBuilder()).clientSampler(clientSampler));

    handler.handleSend(request);

    verify(clientSampler).trySample(request);
  }

  @Test public void handleSendWithParent_overrideContext() {
    try (Scope ws = httpTracing.tracing.currentTraceContext().newScope(context)) {
      brave.Span span = handler.handleSendWithParent(request, null);

      // If the overwrite was successful, we have a root span.
      assertThat(span.context().parentIdAsLong()).isZero();
    }
  }

  @Test public void handleSendWithParent_overrideNull() {
    try (Scope ws = httpTracing.tracing.currentTraceContext().newScope(null)) {
      brave.Span span = handler.handleSendWithParent(request, context);

      // If the overwrite was successful, we have a child span.
      assertThat(span.context().parentIdAsLong()).isEqualTo(context.spanId());
    }
  }

  @Test public void handleReceive_finishesSpanEvenIfUnwrappedNull() {
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

  @Test public void handleReceive_finishesSpanEvenIfUnwrappedNull_withError() {
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

  @Test public void handleReceive_responseRequired() {
    brave.Span span = mock(brave.Span.class);

    assertThatThrownBy(() -> handler.handleReceive(null, span))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("response == null");
  }
}
