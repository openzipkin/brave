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
import brave.propagation.TraceContext;
import brave.rpc.RpcRequest;
import brave.rpc.RpcRequestParser;
import brave.rpc.RpcTracing;
import io.grpc.Metadata;
import io.grpc.ServerCall;

/**
 * @see GrpcServerRequest
 * @see GrpcServerResponse
 * @deprecated Since 5.12 use {@link RpcTracing#serverRequestParser()} or {@link
 * RpcTracing#serverResponseParser()}.
 */
@Deprecated
public class GrpcServerParser extends GrpcParser implements RpcRequestParser {
  @Override public void parse(RpcRequest request, TraceContext context, SpanCustomizer span) {
    if (request instanceof GrpcServerRequest) {
      GrpcServerRequest grpcRequest = (GrpcServerRequest) request;
      onStart(grpcRequest.call, grpcRequest.headers, span);
    } else {
      assert false : "expected a GrpcServerRequest: " + request;
    }
  }

  /** Override the customize the span based on the start of a request. */
  protected <ReqT, RespT> void onStart(ServerCall<ReqT, RespT> call, Metadata headers,
    SpanCustomizer span) {
    span.name(spanName(call.getMethodDescriptor()));
  }

  /**
   * @since 4.8
   * @deprecated Since 5.12 use {@link ServerCall.Listener#onMessage(Object)}.
   */
  @Deprecated @Override protected <M> void onMessageSent(M message, SpanCustomizer span) {
    super.onMessageSent(message, span);
  }

  /**
   * @since 4.8
   * @deprecated Since 5.12 use {@link ServerCall#sendMessage(Object)}.
   */
  @Deprecated @Override protected <M> void onMessageReceived(M message, SpanCustomizer span) {
    super.onMessageReceived(message, span);
  }
}
