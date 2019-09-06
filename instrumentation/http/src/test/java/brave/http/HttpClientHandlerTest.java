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

import brave.ScopedSpan;
import brave.SpanCustomizer;
import brave.Tracing;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpClientHandlerTest {
  List<Span> spans = new ArrayList<>();
  HttpTracing httpTracing;
  @Spy HttpSampler sampler = HttpSampler.TRACE_ID;
  @Spy HttpClientParser parser = new HttpClientParser();
  @Mock HttpClientAdapter<Object, Object> adapter;
  @Mock TraceContext.Injector<Object> injector;
  @Mock Object request;
  @Mock Object response;
  HttpClientHandler<Object, Object> handler;

  @Mock(answer = CALLS_REAL_METHODS) HttpClientRequest defaultRequest;
  @Mock(answer = CALLS_REAL_METHODS) HttpClientResponse defaultResponse;
  HttpClientHandler<HttpClientRequest, HttpClientResponse> defaultHandler;

  @Before public void init() {
    httpTracing = HttpTracing.newBuilder(
      Tracing.newBuilder()
        .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
          .addScopeDecorator(StrictScopeDecorator.create())
          .build())
        .spanReporter(spans::add)
        .build()
    ).clientSampler(new HttpSampler() {
      @Override public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request) {
        return sampler.trySample(adapter, request);
      }
    }).clientParser(parser).build();
    handler = HttpClientHandler.create(httpTracing, adapter);

    when(adapter.method(request)).thenReturn("GET");

    when(defaultRequest.unwrap()).thenReturn(request);
    when(defaultResponse.unwrap()).thenReturn(response);
    defaultHandler = HttpClientHandler.create(httpTracing);
  }

  @After public void close() {
    Tracing.current().close();
  }

  @Test public void handleStart_parsesTagsWithCustomizer() {
    brave.Span span = mock(brave.Span.class);
    brave.SpanCustomizer spanCustomizer = mock(brave.SpanCustomizer.class);
    when(adapter.method(request)).thenReturn("GET");
    when(span.customizer()).thenReturn(spanCustomizer);

    handler.handleStart(adapter, request, span);

    verify(spanCustomizer).name("GET");
    verify(spanCustomizer).tag("http.method", "GET");
    verifyNoMoreInteractions(spanCustomizer);
  }

  @Test public void handleSend_defaultsToMakeNewTrace() {
    assertThat(handler.handleSend(injector, request))
      .extracting(s -> s.isNoop(), s -> s.context().parentId())
      .containsExactly(false, null);
  }

  @Test public void handleSend_makesAChild() {
    ScopedSpan parent = httpTracing.tracing().tracer().startScopedSpan("test");
    try {
      assertThat(handler.handleSend(injector, request))
        .extracting(s -> s.isNoop(), s -> s.context().parentId())
        .containsExactly(false, parent.context().spanId());
    } finally {
      parent.finish();
    }
  }

  @Test public void handleSend_makesRequestBasedSamplingDecision() {
    sampler = mock(HttpSampler.class);
    // request sampler says false eventhough trace ID sampler would have said true
    when(sampler.trySample(adapter, request)).thenReturn(false);

    assertThat(handler.handleSend(injector, request).isNoop())
      .isTrue();
  }

  @Test public void handleSend_injectsTheTraceContext() {
    TraceContext context = handler.handleSend(injector, request).context();

    verify(injector).inject(context, request);
  }

  @Test public void handleSend_injectsTheTraceContext_onTheCarrier() {
    HttpClientRequest customCarrier = mock(HttpClientRequest.class);
    TraceContext context = handler.handleSend(injector, customCarrier, request).context();

    verify(injector).inject(context, customCarrier);
  }

  @Test public void handleSend_addsClientAddressWhenOnlyServiceName() {
    httpTracing = httpTracing.clientOf("remote-service");

    HttpClientHandler.create(httpTracing, adapter).handleSend(injector, request).finish();

    assertThat(spans)
      .extracting(Span::remoteServiceName)
      .containsExactly("remote-service");
  }

  @Test public void handleSend_skipsClientAddressWhenUnparsed() {
    handler.handleSend(injector, request).finish();

    assertThat(spans)
      .extracting(Span::remoteServiceName)
      .containsNull();
  }

  @Test public void externalTimestamps() {
    when(defaultRequest.startTimestamp()).thenReturn(123000L);
    when(defaultResponse.finishTimestamp()).thenReturn(124000L);

    brave.Span span = defaultHandler.handleSend(defaultRequest);
    defaultHandler.handleReceive(defaultResponse, null, span);

    assertThat(spans.get(0).durationAsLong()).isEqualTo(1000L);
  }

  @Test public void handleSend_samplerSeesUnwrappedType() {
    defaultHandler.handleSend(defaultRequest);

    HttpClientRequest.Adapter adapter = new HttpClientRequest.Adapter(defaultRequest);
    verify(sampler).trySample(adapter, request);
  }

  @Test public void nextSpan_samplerSeesUnwrappedType() {
    defaultHandler.nextSpan(defaultRequest);

    HttpClientRequest.Adapter adapter = new HttpClientRequest.Adapter(defaultRequest);
    verify(sampler).trySample(adapter, request);
  }

  @Test public void nextSpan_samplerSeesUnwrappedType_oldHandler() {
    handler.nextSpan(defaultRequest);

    HttpClientRequest.Adapter adapter = new HttpClientRequest.Adapter(defaultRequest);
    verify(sampler).trySample(adapter, request);
  }

  @Test public void handleSend_parserSeesUnwrappedType() {
    brave.Span span = defaultHandler.handleSend(defaultRequest);
    defaultHandler.handleReceive(defaultResponse, null, span);

    HttpClientRequest.Adapter adapter = new HttpClientRequest.Adapter(defaultRequest);
    verify(parser).request(eq(adapter), eq(request), any(SpanCustomizer.class));
  }

  @Test public void handleReceive_parserSeesUnwrappedType() {
    brave.Span span = defaultHandler.handleSend(defaultRequest);
    defaultHandler.handleReceive(defaultResponse, null, span);

    HttpClientResponse.Adapter adapter = new HttpClientResponse.Adapter(defaultResponse);
    verify(parser).response(eq(adapter), eq(response), isNull(), any(SpanCustomizer.class));
  }

  @Test public void handleReceive_parserSeesUnwrappedType_oldHandler() {
    brave.Span span = handler.handleSend(defaultRequest);
    handler.handleReceive(defaultResponse, null, span);

    HttpClientResponse.Adapter adapter = new HttpClientResponse.Adapter(defaultResponse);
    verify(parser).response(eq(adapter), eq(response), isNull(), any(SpanCustomizer.class));
  }
}
