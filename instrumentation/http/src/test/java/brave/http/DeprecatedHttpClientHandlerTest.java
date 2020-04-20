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
package brave.http;

import brave.ScopedSpan;
import brave.Tracing;
import brave.http.HttpClientAdapters.FromRequestAdapter;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
@Deprecated public class DeprecatedHttpClientHandlerTest {
  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(1L).sampled(true).build();
  List<Span> spans = new ArrayList<>();
  @Mock HttpSampler sampler;
  HttpTracing httpTracing;
  HttpClientHandler<Object, Object> handler;

  @Spy HttpClientParser parser = new HttpClientParser();
  @Mock HttpClientAdapter<Object, Object> adapter;
  @Mock TraceContext.Injector<Object> injector;
  @Mock Object request;
  @Mock Object response;

  @Before public void init() {
    httpTracing = HttpTracing.newBuilder(Tracing.newBuilder().spanReporter(spans::add).build())
      .clientSampler(sampler).clientParser(parser).build();
    handler = HttpClientHandler.create(httpTracing, adapter);

    when(adapter.method(request)).thenReturn("GET");
  }

  @After public void close() {
    Tracing.current().close();
  }

  @Test public void handleSend_defaultsToMakeNewTrace() {
    when(sampler.trySample(any(FromRequestAdapter.class))).thenReturn(null);

    assertThat(handler.handleSend(injector, request))
      .extracting(brave.Span::isNoop, s -> s.context().parentId())
      .containsExactly(false, null);
  }

  @Test public void handleSend_makesAChild() {
    ScopedSpan parent = httpTracing.tracing().tracer().startScopedSpan("test");
    try {
      assertThat(handler.handleSend(injector, request))
        .extracting(brave.Span::isNoop, s -> s.context().parentId())
        .containsExactly(false, parent.context().spanId());
    } finally {
      parent.finish();
    }
  }

  @Test public void handleSend_makesRequestBasedSamplingDecision() {
    // request sampler says false eventhough trace ID sampler would have said true
    when(sampler.trySample(any(FromRequestAdapter.class))).thenReturn(false);

    assertThat(handler.handleSend(injector, request).isNoop())
      .isTrue();
  }

  @Test public void handleSend_injectsTheTraceContext() {
    TraceContext context = handler.handleSend(injector, request).context();

    verify(injector).inject(context, request);
  }

  @Test public void handleSend_injectsTheTraceContext_onTheRequest() {
    HttpClientRequest customRequest = mock(HttpClientRequest.class);
    TraceContext context = handler.handleSend(injector, customRequest, request).context();

    verify(injector).inject(context, customRequest);
  }

  @Test public void handleSend_addsClientAddressWhenOnlyServiceName() {
    when(sampler.trySample(any(FromRequestAdapter.class))).thenReturn(null);

    httpTracing = httpTracing.clientOf("remote-service");

    HttpClientHandler.create(httpTracing, adapter).handleSend(injector, request).finish();

    assertThat(spans)
      .extracting(Span::remoteServiceName)
      .containsExactly("remote-service");
  }

  @Test public void handleSend_skipsClientAddressWhenUnparsed() {
    when(sampler.trySample(any(FromRequestAdapter.class))).thenReturn(null);

    handler.handleSend(injector, request).finish();

    assertThat(spans)
      .extracting(Span::remoteServiceName)
      .containsNull();
  }

  @Test public void handleReceive() {
    brave.Span span = mock(brave.Span.class);
    when(span.context()).thenReturn(context);
    when(span.customizer()).thenReturn(span);

    handler.handleReceive(response, null, span);

    verify(parser).response(eq(adapter), eq(response), isNull(), eq(span));
  }
}
