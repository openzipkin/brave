/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rpc;

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
public class RpcHandlerTest {
  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(10L).build();
  @Mock Span span;
  @Mock SpanCustomizer spanCustomizer;
  @Mock RpcRequest request;
  @Mock RpcResponse response;
  @Mock RpcRequestParser requestParser;
  @Mock RpcResponseParser responseParser;
  RpcHandler handler;

  @BeforeEach void init() {
    handler = new RpcHandler(requestParser, responseParser) {
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
    handler = new RpcHandler(RpcRequestParser.DEFAULT, RpcResponseParser.DEFAULT) {
      @Override void parseRequest(RpcRequest request, Span span) {
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
