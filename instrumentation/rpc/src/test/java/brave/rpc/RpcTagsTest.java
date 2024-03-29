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
package brave.rpc;

import brave.SpanCustomizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** This only tests things not already covered in {@code brave.TagTest} */
@ExtendWith(MockitoExtension.class)
public class RpcTagsTest {
  @Mock SpanCustomizer span;
  @Mock RpcRequest request;
  @Mock RpcResponse response;

  @Test void method() {
    when(request.method()).thenReturn("Report");
    RpcTags.METHOD.tag(request, span);

    verify(span).tag("rpc.method", "Report");
  }

  @Test void method_null() {
    RpcTags.METHOD.tag(request, span);

    verifyNoMoreInteractions(span);
  }

  @Test void service() {
    when(request.service()).thenReturn("zipkin.proto3.SpanService");
    RpcTags.SERVICE.tag(request, span);

    verify(span).tag("rpc.service", "zipkin.proto3.SpanService");
  }

  @Test void service_null() {
    RpcTags.SERVICE.tag(request, span);

    verifyNoMoreInteractions(span);
  }

  @Test void error_code() {
    when(response.errorCode()).thenReturn("CANCELLED");
    RpcTags.ERROR_CODE.tag(response, span);

    verify(span).tag("rpc.error_code", "CANCELLED");
  }

  @Test void error_code_null() {
    RpcTags.ERROR_CODE.tag(response, span);

    verifyNoMoreInteractions(span);
  }
}
