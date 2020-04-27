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

import brave.Response;
import brave.Span;
import brave.internal.Nullable;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.Status;

// intentionally not yet public until we add tag parsing functionality
final class GrpcClientResponse extends Response {
  final GrpcClientRequest request;
  @Nullable final Status status;
  @Nullable final Metadata trailers;
  @Nullable final Throwable error;

  GrpcClientResponse(GrpcClientRequest request,
    @Nullable Status status, @Nullable Metadata trailers, @Nullable Throwable error) {
    if (request == null) throw new NullPointerException("request == null");
    this.request = request;
    this.status = status;
    this.trailers = trailers;
    this.error = error != null ? error : status != null ? status.getCause() : null;
  }

  @Override public Span.Kind spanKind() {
    return Span.Kind.CLIENT;
  }

  /** Returns the {@link #status()} */
  @Override @Nullable public Status unwrap() {
    return status;
  }

  @Override public GrpcClientRequest request() {
    return request;
  }

  @Override @Nullable public Throwable error() {
    return error;
  }

  /**
   * Returns the string form of the {@link Status#getCode()} or {@code null} when not {@link
   * Status#isOk()} or {@link #error()}.
   */
  @Nullable public String errorCode() {
    if (status == null || status.isOk()) return null;
    return status.getCode().name();
  }

  /**
   * Returns the status passed to {@link ClientCall.Listener#onClose(Status, Metadata)} or {@code
   * null} on {@link #error()}.
   *
   * @since 5.12
   */
  @Nullable public Status status() {
    return status;
  }

  /**
   * Returns the trailers passed to {@link ClientCall.Listener#onClose(Status, Metadata)} or {@code
   * null} on {@link #error()}.
   *
   * @since 5.12
   */
  @Nullable public Metadata trailers() {
    return trailers;
  }
}
