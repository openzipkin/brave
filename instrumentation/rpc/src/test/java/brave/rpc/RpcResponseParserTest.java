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
public class RpcResponseParserTest {
  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(10L).build();
  @Mock RpcResponse response;
  @Mock SpanCustomizer span;

  RpcResponseParser responseParser = RpcResponseParser.DEFAULT;

  @Test public void responseParser_noData() {
    responseParser.parse(response, context, span);

    verify(response).errorCode();
    verifyNoMoreInteractions(response, span);
  }

  @Test public void responseParser_errorCode_whenErrorNull() {
    when(response.errorCode()).thenReturn("CANCELLED");

    responseParser.parse(response, context, span);

    verify(response).errorCode();
    verify(response).error();
    verify(span).tag("rpc.error_code", "CANCELLED");
    verify(span).tag("error", "CANCELLED");
    verifyNoMoreInteractions(response, span);
  }

  /** Ensure we don't obviate a better "error" tag later when an exception is present. */
  @Test public void responseParser_errorCode_whenError() {
    when(response.error()).thenReturn(new RuntimeException());
    when(response.errorCode()).thenReturn("CANCELLED");

    responseParser.parse(response, context, span);

    verify(response).errorCode();
    verify(response).error();
    verify(span).tag("rpc.error_code", "CANCELLED");
    verifyNoMoreInteractions(response, span);
  }
}
