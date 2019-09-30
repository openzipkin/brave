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
package brave.rpc;

import brave.SpanCustomizer;
import brave.propagation.TraceContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RpcParserTest {
  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(10L).build();
  @Mock SpanCustomizer spanCustomizer;
  @Mock RpcRequest request;
  @Mock RpcResponse response;

  @Test public void request_addsNameServiceAndMethod() {
    when(request.service()).thenReturn("zipkin.proto3.SpanService");
    when(request.method()).thenReturn("Report");

    RpcRequestParser.DEFAULT.parse(request, context, spanCustomizer);

    verify(spanCustomizer).name("zipkin.proto3.SpanService/Report");
    verify(spanCustomizer).tag("rpc.service", "zipkin.proto3.SpanService");
    verify(spanCustomizer).tag("rpc.method", "Report");
    verifyNoMoreInteractions(spanCustomizer);
  }

  @Test public void response_setsErrorTagToErrorCode() {
    when(response.errorCode()).thenReturn("CANCELLED");

    RpcResponseParser.DEFAULT.parse(response, context, spanCustomizer);

    verify(spanCustomizer).tag("rpc.error_code", "CANCELLED");
    verify(spanCustomizer).tag("error", "CANCELLED");
    verifyNoMoreInteractions(spanCustomizer);
  }
}
