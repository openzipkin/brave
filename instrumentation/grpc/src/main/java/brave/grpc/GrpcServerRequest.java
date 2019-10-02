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

import brave.internal.Nullable;
import brave.propagation.Propagation.Getter;
import brave.rpc.RpcServerRequest;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

// intentionally not yet public until we add tag parsing functionality
final class GrpcServerRequest extends RpcServerRequest {
  static final Getter<GrpcServerRequest, Metadata.Key<String>> GETTER =
    new Getter<GrpcServerRequest, Metadata.Key<String>>() { // retrolambda no like
      @Override public String get(GrpcServerRequest request, Metadata.Key<String> key) {
        return request.getMetadata(key);
      }

      @Override public String toString() {
        return "GrpcServerRequest::getMetadata";
      }
    };

  final String fullMethodName;
  final Metadata metadata;

  GrpcServerRequest(MethodDescriptor<?, ?> methodDescriptor, Metadata metadata) {
    if (methodDescriptor == null) throw new NullPointerException("methodDescriptor == null");
    this.fullMethodName = methodDescriptor.getFullMethodName();
    if (metadata == null) throw new NullPointerException("metadata == null");
    this.metadata = metadata;
  }

  @Override public Object unwrap() {
    return this;
  }

  @Override public String method() {
    return GrpcParser.method(fullMethodName);
  }

  @Override public String service() {
    return GrpcParser.service(fullMethodName);
  }

  @Nullable <T> T getMetadata(Metadata.Key<T> key) {
    return metadata.get(key);
  }
}
