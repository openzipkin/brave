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
package brave.grpc;

import brave.SpanCustomizer;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

public class GrpcClientParser extends GrpcParser {
  /** Override the customize the span based on the start of a request. */
  protected <ReqT, RespT> void onStart(MethodDescriptor<ReqT, RespT> method, CallOptions options,
    Metadata headers, SpanCustomizer span) {
    span.name(spanName(method));
  }

  /**
   * @since 4.8
   * @deprecated Since 5.12 use {@link ClientCall#sendMessage(Object)}.
   */
  @Deprecated @Override protected <M> void onMessageSent(M message, SpanCustomizer span) {
    super.onMessageSent(message, span);
  }

  /**
   * @since 4.8
   * @deprecated Since 5.12 use {@link ClientCall.Listener#onMessage(Object)}.
   */
  @Deprecated @Override protected <M> void onMessageReceived(M message, SpanCustomizer span) {
    super.onMessageReceived(message, span);
  }
}
