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
import brave.rpc.RpcServerResponse;
import brave.rpc.RpcTracing;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.Status;

/**
 * Allows access gRPC specific aspects of a client response for parsing.
 *
 * @see GrpcServerRequest
 * @see RpcTracing#serverResponseParser()
 * @since 5.12
 */
public final class GrpcServerResponse extends RpcServerResponse {
  final GrpcServerRequest request;
  final Status status;
  final Metadata trailers;

  GrpcServerResponse(GrpcServerRequest request, Status status, Metadata trailers) {
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

  @Override public GrpcServerRequest request() {
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
   * Returns the status passed to{@link ServerCall#close(Status, Metadata)}.
   *
   * @since 5.12
   */
  public Status status() {
    return status;
  }

  /**
   * Returns the trailers passed to {@link ServerCall#close(Status, Metadata)}.
   *
   * @since 5.12
   */
  public Metadata trailers() {
    return trailers;
  }
}
