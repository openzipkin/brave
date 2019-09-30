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

import brave.ErrorParser;
import brave.Span;
import brave.SpanCustomizer;
import brave.Tracing;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import brave.rpc.RpcResponse;
import brave.rpc.RpcResponseParser;
import brave.rpc.RpcTracing;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

/** @deprecated Since 5.12 use parsers in {@link RpcTracing}. */
@Deprecated
public class GrpcParser extends MessageProcessor implements RpcResponseParser {
  static final RpcResponseParser LEGACY_RESPONSE_PARSER = new GrpcParser();

  @Override
  public void parse(RpcResponse response, TraceContext context, SpanCustomizer span) {
    Status status = null;
    Metadata trailers = null;
    if (response instanceof GrpcClientResponse) {
      status = ((GrpcClientResponse) response).status();
      trailers = ((GrpcClientResponse) response).trailers();
    } else if (response instanceof GrpcServerResponse) {
      status = ((GrpcServerResponse) response).status();
      trailers = ((GrpcServerResponse) response).trailers();
    } else {
      assert false : "expected a GrpcClientResponse or GrpcServerResponse: " + response;
    }
    onClose(status, trailers, span);
  }

  /**
   * Override when making custom types. Typically, you'll use {@link Tracing#errorParser()}
   *
   * <pre>{@code
   * class MyGrpcParser extends GrpcParser {
   *   ErrorParser errorParser;
   *
   *   MyGrpcParser(Tracing tracing) {
   *     errorParser = tracing.errorParser();
   *   }
   *
   *   protected ErrorParser errorParser() {
   *     return errorParser;
   *   }
   * --snip--
   * }</pre>
   */
  protected ErrorParser errorParser() {
    return ErrorParser.get();
  }

  /** Returns the span name of the request. Defaults to the full grpc method name. */
  protected <ReqT, RespT> String spanName(MethodDescriptor<ReqT, RespT> methodDescriptor) {
    return methodDescriptor.getFullMethodName();
  }

  /**
   * Override to change what data from the status or trailers are parsed into the span modeling it.
   *
   * <p><em>Note</em>: {@link Status#getCause()} will be set as {@link Span#error(Throwable)} by
   * default. You don't need to parse it here.
   */
  protected void onClose(Status status, Metadata trailers, SpanCustomizer span) {
    if (status == null || status.isOk()) return;

    String code = String.valueOf(status.getCode());
    span.tag("grpc.status_code", code);
    if (status.getCause() == null) span.tag("error", code);
  }

  static @Nullable String method(String fullMethodName) {
    int index = fullMethodName.lastIndexOf('/');
    if (index == -1 || index == 0) return null;
    return fullMethodName.substring(index + 1);
  }

  static @Nullable String service(String fullMethodName) {
    int index = fullMethodName.lastIndexOf('/');
    if (index == -1 || index == 0) return null;
    return fullMethodName.substring(0, index);
  }
}
