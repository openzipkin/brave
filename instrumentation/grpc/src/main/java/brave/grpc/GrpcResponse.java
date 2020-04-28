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

import brave.rpc.RpcTracing;
import io.grpc.Metadata;
import io.grpc.Status;

/**
 * Allows access gRPC specific aspects of a client or server response for parsing.
 *
 * <p>Here's an example that adds default tags, and if gRPC, the response encoding:
 * <pre>{@code
 * Tag<GrpcResponse> responseEncoding = new Tag<GrpcResponse>("grpc.response_encoding") {
 *   @Override protected String parseValue(GrpcResponse input, TraceContext context) {
 *     return input.headers().get(GrpcUtil.MESSAGE_ENCODING_KEY);
 *   }
 * };
 *
 * RpcResponseParser addResponseEncoding = (res, context, span) -> {
 *   RpcResponseParser.DEFAULT.parse(res, context, span);
 *   if (res instanceof GrpcResponse) responseEncoding.tag((GrpcResponse) res, span);
 * };
 *
 * grpcTracing = GrpcTracing.create(RpcTracing.newBuilder(tracing)
 *     .clientResponseParser(addResponseEncoding);
 *     .serverResponseParser(addResponseEncoding).build());
 * }</pre>
 *
 * @see GrpcRequest
 * @see GrpcClientResponse
 * @see GrpcServerResponse
 * @see RpcTracing#clientResponseParser()
 * @see RpcTracing#serverResponseParser()
 * @since 5.12
 */
// NOTE: gRPC is Java 1.7+, so we cannot add methods to this later
public interface GrpcResponse {
  Metadata headers();

  Status status();

  Metadata trailers();
}
