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

import brave.Span;
import brave.SpanCustomizer;
import brave.handler.FinishedSpanHandler;
import brave.propagation.TraceContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpHandlerTest {
  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(10L).build();
  @Mock brave.Span span;
  @Mock SpanCustomizer spanCustomizer;
  @Mock HttpRequest request;
  @Mock HttpResponse response;
  HttpHandler handler;

  @Before public void init() {
    handler = new HttpHandler(HttpRequestParser.DEFAULT, HttpResponseParser.DEFAULT) {
      @Override void parseRequest(HttpRequest request, Span span) {
        requestParser.parse(request, span.context(), span.customizer());
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

  @Test public void handleStart_parsesTagsWithCustomizer() {
    brave.Span span = mock(brave.Span.class);
    when(span.context()).thenReturn(context);
    brave.SpanCustomizer spanCustomizer = mock(brave.SpanCustomizer.class);
    when(request.spanKind()).thenReturn(Span.Kind.SERVER);
    when(request.method()).thenReturn("GET");
    when(span.customizer()).thenReturn(spanCustomizer);

    handler.handleStart(request, span);

    verify(span).kind(Span.Kind.SERVER);
    verify(spanCustomizer).name("GET");
    verify(spanCustomizer).tag("http.method", "GET");
    verifyNoMoreInteractions(spanCustomizer);
  }

  @Test public void handleStart_addsRemoteEndpointWhenParsed() {
    handler = new HttpHandler(HttpRequestParser.DEFAULT, HttpResponseParser.DEFAULT) {
      @Override void parseRequest(HttpRequest request, Span span) {
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
    when(response.statusCode()).thenReturn(404);
    when(span.customizer()).thenReturn(spanCustomizer);

    handler.handleFinish(response, null, span);

    verify(spanCustomizer).tag("http.status_code", "404");
    verify(spanCustomizer).tag("error", "404");
    verifyNoMoreInteractions(spanCustomizer);
  }

  /** Allows {@link FinishedSpanHandler} to see the error regardless of parsing. */
  @Test public void handleFinish_errorRecordedInSpan() {
    RuntimeException error = new RuntimeException("foo");
    when(response.statusCode()).thenReturn(404);
    when(span.customizer()).thenReturn(spanCustomizer);

    handler.handleFinish(response, error, span);

    verify(span).error(error);
  }

  @Test public void handleFinish_finishedEvenIfAdapterThrows() {
    when(response.statusCode()).thenThrow(new RuntimeException());

    assertThatThrownBy(() -> handler.handleFinish(response, null, span))
      .isInstanceOf(RuntimeException.class);

    verify(span).finish();
  }
}
