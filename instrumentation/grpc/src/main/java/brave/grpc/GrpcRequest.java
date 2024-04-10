/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.grpc;

import brave.rpc.RpcTracing;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * Allows access gRPC specific aspects of a client or server request during sampling and parsing.
 *
 * <p>Here's an example that adds default tags, and if gRPC, the {@linkplain
 * MethodDescriptor#getType() method type}:
 * <pre>{@code
 * Tag<GrpcRequest> methodType = new Tag<GrpcRequest>("grpc.method_type") {
 *   @Override protected String parseValue(GrpcRequest input, TraceContext context) {
 *     return input.methodDescriptor().getType().name();
 *   }
 * };
 *
 * RpcRequestParser addMethodType = (req, context, span) -> {
 *   RpcRequestParser.DEFAULT.parse(req, context, span);
 *   if (req instanceof GrpcRequest) methodType.tag((GrpcRequest) req, span);
 * };
 *
 * grpcTracing = GrpcTracing.create(RpcTracing.newBuilder(tracing)
 *     .clientRequestParser(addMethodType)
 *     .serverRequestParser(addMethodType).build());
 * }</pre>
 *
 * @see GrpcResponse
 * @see GrpcClientRequest
 * @see GrpcServerRequest
 * @see RpcTracing#clientRequestParser()
 * @see RpcTracing#serverRequestParser()
 * @since 5.12
 */
// NOTE: gRPC is Java 1.7+, so we cannot add methods to this later
public interface GrpcRequest {
  // method would be a nicer name, but this is used in instanceof with an RpcRequest
  // and RpcRequest.method() has a String result
  MethodDescriptor<?, ?> methodDescriptor();

  Metadata headers();
}
