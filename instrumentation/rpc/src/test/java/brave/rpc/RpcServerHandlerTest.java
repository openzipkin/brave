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
package brave.rpc;

import brave.Span;
import brave.Tracing;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunction;
import brave.sampler.SamplerFunctions;
import brave.test.TestSpanHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RpcServerHandlerTest {
  TestSpanHandler spans = new TestSpanHandler();
  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(1L).sampled(true).build();

  RpcTracing httpTracing;
  RpcServerHandler handler;

  @Mock(answer = CALLS_REAL_METHODS) RpcServerRequest request;
  @Mock(answer = CALLS_REAL_METHODS) RpcServerResponse response;

  @Before public void init() {
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

  @After public void close() {
    Tracing current = Tracing.current();
    if (current != null) current.close();
  }

  @Test public void handleReceive_traceIdSamplerSpecialCased() {
    Sampler sampler = mock(Sampler.class);

    init(httpTracingBuilder(tracingBuilder().sampler(sampler))
        .serverSampler(SamplerFunctions.deferDecision()));

    assertThat(handler.handleReceive(request).isNoop()).isTrue();

    verify(sampler).isSampled(anyLong());
  }

  @Test public void handleReceive_neverSamplerSpecialCased() {
    Sampler sampler = mock(Sampler.class);

    init(httpTracingBuilder(tracingBuilder().sampler(sampler))
        .serverSampler(SamplerFunctions.neverSample()));

    assertThat(handler.handleReceive(request).isNoop()).isTrue();

    verifyNoMoreInteractions(sampler);
  }

  @Test public void handleReceive_samplerSeesRpcServerRequest() {
    SamplerFunction<RpcRequest> serverSampler = mock(SamplerFunction.class);
    init(httpTracingBuilder(tracingBuilder()).serverSampler(serverSampler));

    handler.handleReceive(request);

    verify(serverSampler).trySample(request);
  }

  @Test public void externalTimestamps() {
    when(request.startTimestamp()).thenReturn(123000L);
    when(response.finishTimestamp()).thenReturn(124000L);

    Span span = handler.handleReceive(request);
    handler.handleSend(response, span);

    assertThat(spans.get(0).startTimestamp()).isEqualTo(123000L);
    assertThat(spans.get(0).finishTimestamp()).isEqualTo(124000L);
  }

  @Test public void handleSend_finishesSpanEvenIfUnwrappedNull() {
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

  @Test public void handleSend_finishesSpanEvenIfUnwrappedNull_withError() {
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

  @Test public void handleSend_oneOfResponseError() {
    brave.Span span = mock(brave.Span.class);

    assertThatThrownBy(() -> handler.handleSend(null, span))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("response == null");
  }
}
