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
import brave.SpanCustomizer;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpHandlerTest {
  CurrentTraceContext currentTraceContext = ThreadLocalCurrentTraceContext.create();
  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(10L).build();
  @Mock HttpAdapter<Object, Object> adapter;
  @Mock brave.Span span;
  @Mock SpanCustomizer spanCustomizer;
  Object request = new Object(), response = new Object();
  HttpHandler<Object, Object, HttpAdapter<Object, Object>> handler;

  @Before public void init() {
    handler = new HttpHandler(currentTraceContext, adapter, new HttpParser()) {
      @Override void parseRequest(Object request, Span span) {
      }
    };
    when(span.context()).thenReturn(context);
    when(span.customizer()).thenReturn(spanCustomizer);
  }

  @Test public void handleStart_nothingOnNoop_success() {
    when(span.isNoop()).thenReturn(true);

    handler.handleStart(request, span);

    verify(span, never()).start();
  }

  @Test public void handleStart_parsesTagsInScope() {
    handler = new HttpHandler(currentTraceContext, adapter, new HttpParser()) {
      @Override void parseRequest(Object request, Span span) {
        assertThat(currentTraceContext.get()).isNotNull();
      }
    };

    handler.handleStart(request, span);
  }

  @Test public void handleStart_addsRemoteEndpointWhenParsed() {
    handler = new HttpHandler(currentTraceContext, adapter, new HttpParser()) {
      @Override void parseRequest(Object request, Span span) {
        span.remoteIpAndPort("1.2.3.4", 0);
      }
    };

    handler.handleStart(request, span);

    verify(span).remoteIpAndPort("1.2.3.4", 0);
  }

  @Test public void handleFinish_nothingOnNoop_success() {
    when(span.isNoop()).thenReturn(true);

    handler.handleFinish(response, null, span);

    verify(span, never()).finish();
  }

  @Test public void handleFinish_nothingOnNoop_error() {
    when(span.isNoop()).thenReturn(true);

    handler.handleFinish(null, new RuntimeException("drat"), span);

    verify(span, never()).finish();
  }

  @Test public void handleFinish_parsesTagsWithCustomizer() {
    when(adapter.statusCodeAsInt(response)).thenReturn(404);
    when(span.customizer()).thenReturn(spanCustomizer);

    handler.handleFinish(response, null, span);

    verify(spanCustomizer).tag("http.status_code", "404");
    verify(spanCustomizer).tag("error", "404");
    verifyNoMoreInteractions(spanCustomizer);
  }

  @Test public void handleFinish_parsesTagsInScope() {
    handler = new HttpHandler(currentTraceContext, adapter, new HttpParser() {
      @Override public <Resp> void response(HttpAdapter<?, Resp> adapter, Resp res, Throwable error,
        SpanCustomizer customizer) {
        assertThat(currentTraceContext.get()).isNotNull();
      }
    }) {
      @Override void parseRequest(Object request, Span span) {
      }
    };

    handler.handleFinish(response, null, span);
  }

  @Test public void handleFinish_finishesWhenSpanNotInScope() {
    doAnswer(invocation -> {
      assertThat(currentTraceContext.get()).isNull();
      return null;
    }).when(span).finish();

    handler.handleFinish(response, null, span);
  }

  @Test public void handleFinish_finishesWhenSpanNotInScope_clearingIfNecessary() {
    try (CurrentTraceContext.Scope ws = currentTraceContext.newScope(context)) {
      handleFinish_finishesWhenSpanNotInScope();
    }
  }

  @Test public void handleFinish_finishedEvenIfAdapterThrows() {
    when(adapter.statusCodeAsInt(response)).thenThrow(new RuntimeException());

    try {
      handler.handleFinish(response, null, span);
      failBecauseExceptionWasNotThrown(RuntimeException.class);
    } catch (RuntimeException e) {
      verify(span).finish();
    }
  }
}
