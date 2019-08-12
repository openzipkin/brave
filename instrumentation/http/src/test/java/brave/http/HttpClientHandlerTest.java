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
import org.mockito.junit.MockitoJUnitRunner;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpClientHandlerTest {
  List<Span> spans = new ArrayList<>();
  HttpTracing httpTracing;
  HttpSampler sampler = HttpSampler.TRACE_ID;
  @Mock HttpClientAdapter<Object, Object> adapter;
  @Mock TraceContext.Injector<Object> injector;
  Object request = new Object();
  HttpClientHandler<Object, Object> handler;

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
    }).build();
    handler = HttpClientHandler.create(httpTracing, adapter);

    when(adapter.method(request)).thenReturn("GET");
  }

  @After public void close() {
    Tracing.current().close();
  }

  @Test public void handleStart_parsesTagsWithCustomizer() {
    brave.Span span = mock(brave.Span.class);
    brave.SpanCustomizer spanCustomizer = mock(brave.SpanCustomizer.class);
    when(adapter.method(request)).thenReturn("GET");
    when(span.customizer()).thenReturn(spanCustomizer);

    handler.handleStart(request, span);

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
    Object customCarrier = new Object();
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
}
