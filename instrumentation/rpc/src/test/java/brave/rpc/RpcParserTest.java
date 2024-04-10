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
public class RpcParserTest {
  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(10L).build();
  @Mock SpanCustomizer spanCustomizer;
  @Mock RpcRequest request;
  @Mock RpcResponse response;

  @Test void request_addsNameServiceAndMethod() {
    when(request.service()).thenReturn("zipkin.proto3.SpanService");
    when(request.method()).thenReturn("Report");

    RpcRequestParser.DEFAULT.parse(request, context, spanCustomizer);

    verify(spanCustomizer).name("zipkin.proto3.SpanService/Report");
    verify(spanCustomizer).tag("rpc.service", "zipkin.proto3.SpanService");
    verify(spanCustomizer).tag("rpc.method", "Report");
    verifyNoMoreInteractions(spanCustomizer);
  }

  @Test void response_setsErrorTagToErrorCode() {
    when(response.errorCode()).thenReturn("CANCELLED");

    RpcResponseParser.DEFAULT.parse(response, context, spanCustomizer);

    verify(spanCustomizer).tag("rpc.error_code", "CANCELLED");
    verify(spanCustomizer).tag("error", "CANCELLED");
    verifyNoMoreInteractions(spanCustomizer);
  }
}
