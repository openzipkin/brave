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

import brave.internal.Nullable;
import brave.rpc.RpcClientResponse;
import brave.rpc.RpcTracing;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.Status;

/**
 * Allows access gRPC specific aspects of a client response for parsing.
 *
 * <p>Here's an example that adds default tags, and if gRPC, the Java result:
 * <pre>{@code
 * rpcTracing = rpcTracingBuilder
 *   .clientResponseParser((res, context, span) -> {
 *      RpcResponseParser.DEFAULT.parse(res, context, span);
 *      if (res instanceof DubboResponse) {
 *        DubboResponse dubboResponse = (DubboResponse) res;
 *        if (res.result() != null) {
 *          tagJavaResult(res.result().value());
 *        }
 *      }
 *   }).build();
 * }</pre>
 *
 * @see GrpcClientRequest
 * @see RpcTracing#clientResponseParser()
 * @since 5.12
 */
public final class GrpcClientResponse extends RpcClientResponse {
  final GrpcClientRequest request;
  final Status status;
  final Metadata trailers;

  GrpcClientResponse(GrpcClientRequest request, Status status, Metadata trailers) {
    if (request == null) throw new NullPointerException("request == null");
    if (status == null) throw new NullPointerException("status == null");
    if (trailers == null) throw new NullPointerException("trailers == null");
    this.request = request;
    this.status = status;
    this.trailers = trailers;
  }

  /** Returns the {@link #status()} */
  @Override public Status unwrap() {
    return status;
  }

  @Override public GrpcClientRequest request() {
    return request;
  }

  /** Returns {@link Status#getCause()} */
  @Override @Nullable public Throwable error() {
    return status.getCause();
  }

  /**
   * Returns the string form of the {@link Status#getCode()} or {@code null} when not {@link
   * Status#isOk()} or {@link #error()}.
   */
  @Override @Nullable public String errorCode() {
    if (status.isOk()) return null;
    return status.getCode().name();
  }

  /**
   * Returns the status passed to {@link ClientCall.Listener#onClose(Status, Metadata)}.
   *
   * @since 5.12
   */
  public Status status() {
    return status;
  }

  /**
   * Returns the trailers passed to {@link ClientCall.Listener#onClose(Status, Metadata)}.
   *
   * @since 5.12
   */
  public Metadata trailers() {
    return trailers;
  }
}
