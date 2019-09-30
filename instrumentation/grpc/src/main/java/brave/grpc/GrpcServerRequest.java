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

import brave.rpc.RpcServerRequest;
import brave.rpc.RpcTracing;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerInterceptor;
import java.util.Map;

/**
 * Allows access gRPC specific aspects of a server request during sampling and parsing.
 *
 * <p>Here's an example that adds default tags, and if gRPC, the {@linkplain
 * MethodDescriptor#getType() method type}:
 * <pre>{@code
 * Tag<GrpcServerRequest> methodType = new Tag<GrpcServerRequest>("grpc.method_type") {
 *   protected String parseValue(GrpcServerRequest input, TraceContext context) {
 *     return input.call().getMethodDescriptor().getType().name();
 *   }
 * };
 * rpcTracing = rpcTracingBuilder.serverResponseParser((res, context, span) -> {
 *   RpcResponseParser.DEFAULT.parse(res, context, span);
 *     if (res instanceof GrpcServerRequest) {
 *       methodType.tag((GrpcServerRequest) res, span);
 *     }
 *   }).build();
 * }</pre>
 *
 * @see GrpcServerResponse
 * @see RpcTracing#serverRequestParser()
 * @since 5.12
 */
public class GrpcServerRequest extends RpcServerRequest {
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
   * Returns the {@linkplain Metadata headers} passed to {@link ServerInterceptor#interceptCall}.
   *
   * @since 5.12
   */
  public Metadata headers() {
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
