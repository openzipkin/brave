/*
 * Copyright 2013-2023 The OpenZipkin Authors
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
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // TODO: hunt down these
public class HttpHandlerTest {
  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(10L).build();
  @Mock Span span;
  @Mock SpanCustomizer spanCustomizer;
  @Mock HttpRequest request;
  @Mock HttpResponse response;
  @Mock HttpRequestParser requestParser;
  @Mock HttpResponseParser responseParser;
  HttpHandler handler;

  @BeforeEach void init() {
    handler = new HttpHandler(requestParser, responseParser) {
    };
    when(span.context()).thenReturn(context);
    when(span.customizer()).thenReturn(spanCustomizer);
  }

  @Test void handleStart_nothingOnNoop_success() {
    when(span.isNoop()).thenReturn(true);

    handler.handleStart(request, span);

    verify(span, never()).start();
  }

  @Test void handleStart_parsesTagsWithCustomizer() {
    when(span.isNoop()).thenReturn(false);
    when(request.spanKind()).thenReturn(Span.Kind.SERVER);

    handler.handleStart(request, span);

    verify(requestParser).parse(request, context, spanCustomizer);
  }

  @Test void handleStart_addsRemoteEndpointWhenParsed() {
    handler = new HttpHandler(HttpRequestParser.DEFAULT, HttpResponseParser.DEFAULT) {
      @Override void parseRequest(HttpRequest request, Span span) {
        span.remoteIpAndPort("1.2.3.4", 0);
      }
    };

    handler.handleStart(request, span);

    verify(span).remoteIpAndPort("1.2.3.4", 0);
  }

  @Test void handleStart_startedEvenIfParsingThrows() {
    when(span.isNoop()).thenReturn(false);
    doThrow(new RuntimeException()).when(requestParser).parse(request, context, spanCustomizer);

    handler.handleStart(request, span);

    verify(span).start();
  }

  @Test void handleFinish_nothingOnNoop() {
    when(span.isNoop()).thenReturn(true);

    handler.handleFinish(response, span);

    verify(span, never()).finish();
  }

  @Test void handleFinish_parsesTagsWithCustomizer() {
    when(span.customizer()).thenReturn(spanCustomizer);

    handler.handleFinish(response, span);

    verify(responseParser).parse(response, context, spanCustomizer);
  }

  /** Allows {@link SpanHandler} to see the error regardless of parsing. */
  @Test void handleFinish_errorRecordedInSpan() {
    RuntimeException error = new RuntimeException("foo");
    when(response.error()).thenReturn(error);

    handler.handleFinish(response, span);

    verify(span).error(error);
  }

  @Test void handleFinish_finishedEvenIfParsingThrows() {
    when(span.isNoop()).thenReturn(false);
    doThrow(new RuntimeException()).when(responseParser).parse(response, context, spanCustomizer);

    handler.handleFinish(response, span);

    verify(span).finish();
  }
}
