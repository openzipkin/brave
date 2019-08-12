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

import brave.SpanCustomizer;
import io.grpc.CallOptions;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

public class GrpcClientParser extends GrpcParser {
  /** Override the customize the span based on the start of a request. */
  protected <ReqT, RespT> void onStart(MethodDescriptor<ReqT, RespT> method, CallOptions options,
    Metadata headers, SpanCustomizer span) {
    span.name(spanName(method));
  }
}
