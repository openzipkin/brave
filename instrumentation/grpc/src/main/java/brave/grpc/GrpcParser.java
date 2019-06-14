/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
import brave.SpanCustomizer;
import brave.Tracing;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

public class GrpcParser {
  static final ErrorParser DEFAULT_ERROR_PARSER = new ErrorParser();

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
    return DEFAULT_ERROR_PARSER;
  }

  /** Returns the span name of the request. Defaults to the full grpc method name. */
  protected <ReqT, RespT> String spanName(MethodDescriptor<ReqT, RespT> methodDescriptor) {
    return methodDescriptor.getFullMethodName();
  }

  /** Override to customize the span based on a message sent to the peer. */
  protected <M> void onMessageSent(M message, SpanCustomizer span) {
  }

  /** Override to customize the span based on the message received from the peer. */
  protected <M> void onMessageReceived(M message, SpanCustomizer span) {
  }

  /**
   * Override to change what data from the status or trailers are parsed into the span modeling it.
   * By default, this tags "grpc.status_code" and "error" when it is not OK.
   */
  protected void onClose(Status status, Metadata trailers, SpanCustomizer span) {
    if (status != null && status.getCode() != Status.Code.OK) {
      String code = String.valueOf(status.getCode());
      span.tag("grpc.status_code", code);
      span.tag("error", code);
    }
  }
}
