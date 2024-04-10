/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
