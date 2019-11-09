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
import brave.propagation.Propagation.Setter;
import brave.rpc.RpcClientRequest;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

// intentionally not yet public until we add tag parsing functionality
final class GrpcClientRequest extends RpcClientRequest {
  static final Setter<GrpcClientRequest, Metadata.Key<String>> SETTER =
    new Setter<GrpcClientRequest, Metadata.Key<String>>() { // retrolambda no like
      @Override public void put(GrpcClientRequest request, Metadata.Key<String> key, String value) {
        request.setMetadata(key, value);
      }

      @Override public String toString() {
        return "GrpcClientRequest::setMetadata";
      }
    };

  final String fullMethodName;
  @Nullable volatile Metadata metadata; // nullable due to lifecycle of gRPC request

  GrpcClientRequest(MethodDescriptor<?, ?> methodDescriptor) {
    if (methodDescriptor == null) throw new NullPointerException("methodDescriptor == null");
    this.fullMethodName = methodDescriptor.getFullMethodName();
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

  /** Call on {@link ClientCall#start(ClientCall.Listener, Metadata)} */
  GrpcClientRequest metadata(Metadata metadata) {
    this.metadata = metadata;
    return this;
  }

  <T> void setMetadata(Metadata.Key<T> key, T value) {
    Metadata metadata = this.metadata;
    if (metadata == null) {
      assert false : "This code should never be called when null";
      return;
    }
    metadata.removeAll(key);
    metadata.put(key, value);
  }
}
