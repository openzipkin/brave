/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rpc;

import brave.SpanCustomizer;
import brave.propagation.TraceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RpcResponseParserTest {
  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(10L).build();
  @Mock RpcResponse response;
  @Mock SpanCustomizer span;

  RpcResponseParser responseParser = RpcResponseParser.DEFAULT;

  @Test void responseParser_noData() {
    responseParser.parse(response, context, span);

    verify(response).errorCode();
    verifyNoMoreInteractions(response, span);
  }

  @Test void responseParser_errorCode_whenErrorNull() {
    when(response.errorCode()).thenReturn("CANCELLED");

    responseParser.parse(response, context, span);

    verify(response).errorCode();
    verify(response).error();
    verify(span).tag("rpc.error_code", "CANCELLED");
    verify(span).tag("error", "CANCELLED");
    verifyNoMoreInteractions(response, span);
  }

  /** Ensure we don't obviate a better "error" tag later when an exception is present. */
  @Test void responseParser_errorCode_whenError() {
    when(response.error()).thenReturn(new RuntimeException());
    when(response.errorCode()).thenReturn("CANCELLED");

    responseParser.parse(response, context, span);

    verify(response).errorCode();
    verify(response).error();
    verify(span).tag("rpc.error_code", "CANCELLED");
    verifyNoMoreInteractions(response, span);
  }
}
