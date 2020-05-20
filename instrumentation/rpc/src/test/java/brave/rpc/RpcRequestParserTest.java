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
public class RpcRequestParserTest {
  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(10L).build();
  @Mock RpcRequest request;
  @Mock SpanCustomizer span;

  RpcRequestParser requestParser = RpcRequestParser.DEFAULT;

  @Test public void requestParser_noData() {
    requestParser.parse(request, context, span);

    verify(request).service();
    verify(request).method();
    verifyNoMoreInteractions(request, span);
  }

  @Test public void requestParser_onlyMethod() {
    when(request.method()).thenReturn("Report");

    requestParser.parse(request, context, span);

    verify(request).service();
    verify(request).method();
    verify(span).tag("rpc.method", "Report");
    verify(span).name("Report");
    verifyNoMoreInteractions(request, span);
  }

  @Test public void requestParser_onlyService() {
    when(request.service()).thenReturn("zipkin.proto3.SpanService");

    requestParser.parse(request, context, span);

    verify(request).service();
    verify(request).method();
    verify(span).tag("rpc.service", "zipkin.proto3.SpanService");
    verify(span).name("zipkin.proto3.SpanService");
    verifyNoMoreInteractions(request, span);
  }

  @Test public void requestParser_methodAndService() {
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
