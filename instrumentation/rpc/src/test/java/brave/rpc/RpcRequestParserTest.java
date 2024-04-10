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
public class RpcRequestParserTest {
  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(10L).build();
  @Mock RpcRequest request;
  @Mock SpanCustomizer span;

  RpcRequestParser requestParser = RpcRequestParser.DEFAULT;

  @Test void requestParser_noData() {
    requestParser.parse(request, context, span);

    verify(request).service();
    verify(request).method();
    verifyNoMoreInteractions(request, span);
  }

  @Test void requestParser_onlyMethod() {
    when(request.method()).thenReturn("Report");

    requestParser.parse(request, context, span);

    verify(request).service();
    verify(request).method();
    verify(span).tag("rpc.method", "Report");
    verify(span).name("Report");
    verifyNoMoreInteractions(request, span);
  }

  @Test void requestParser_onlyService() {
    when(request.service()).thenReturn("zipkin.proto3.SpanService");

    requestParser.parse(request, context, span);

    verify(request).service();
    verify(request).method();
    verify(span).tag("rpc.service", "zipkin.proto3.SpanService");
    verify(span).name("zipkin.proto3.SpanService");
    verifyNoMoreInteractions(request, span);
  }

  @Test void requestParser_methodAndService() {
    when(request.service()).thenReturn("zipkin.proto3.SpanService");
    when(request.method()).thenReturn("Report");

    requestParser.parse(request, context, span);

    verify(request).service();
    verify(request).method();
    verify(span).tag("rpc.method", "Report");
    verify(span).tag("rpc.service", "zipkin.proto3.SpanService");
    verify(span).name("zipkin.proto3.SpanService/Report");
    verifyNoMoreInteractions(request, span);
  }
}
