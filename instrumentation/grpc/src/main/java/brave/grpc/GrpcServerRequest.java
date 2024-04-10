/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.grpc;

import brave.rpc.RpcServerRequest;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerInterceptor;
import java.util.Map;

/**
 * Allows access gRPC specific aspects of a server request during sampling and parsing.
 *
 * @see GrpcServerResponse
 * @see GrpcRequest for a parsing example
 * @since 5.12
 */
public class GrpcServerRequest extends RpcServerRequest implements GrpcRequest {
  final Map<String, Key<String>> nameToKey;
  final ServerCall<?, ?> call;
  final Metadata headers;

  GrpcServerRequest(Map<String, Key<String>> nameToKey, ServerCall<?, ?> call, Metadata headers) {
    if (nameToKey == null) throw new NullPointerException("nameToKey == null");
    if (call == null) throw new NullPointerException("call == null");
    if (headers == null) throw new NullPointerException("headers == null");
    this.nameToKey = nameToKey;
    this.call = call;
    this.headers = headers;
  }

  /** Returns the {@link #call()} */
  @Override public Object unwrap() {
    return call;
  }

  @Override public String method() {
    return GrpcParser.method(call.getMethodDescriptor().getFullMethodName());
  }

  @Override public String service() {
    // MethodDescriptor.getServiceName() is not in our floor version: gRPC 1.2
    return GrpcParser.service(call.getMethodDescriptor().getFullMethodName());
  }

  /**
   * Returns the {@linkplain ServerCall server call} passed to {@link
   * ServerInterceptor#interceptCall}.
   *
   * @since 5.12
   */
  public ServerCall<?, ?> call() {
    return call;
  }

  /**
   * Returns {@linkplain ServerCall#getMethodDescriptor()}} from the {@link #call()}.
   *
   * @since 5.12
   */
  @Override public MethodDescriptor<?, ?> methodDescriptor() {
    return call.getMethodDescriptor();
  }

  /**
   * Returns the {@linkplain Metadata headers} passed to {@link ServerInterceptor#interceptCall}.
   *
   * @since 5.12
   */
  @Override public Metadata headers() {
    return headers;
  }

  @Override protected String propagationField(String keyName) {
    if (keyName == null) throw new NullPointerException("keyName == null");
    Key<String> key = nameToKey.get(keyName);
    if (key == null) {
      assert false : "We currently don't support getting headers except propagation fields";
      return null;
    }
    return headers.get(key);
  }
}
