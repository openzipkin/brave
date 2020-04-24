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
import brave.propagation.Propagation.Setter;
import brave.rpc.RpcClientRequest;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.MethodDescriptor;
import java.util.Map;

// intentionally not yet public until we add tag parsing functionality
final class GrpcClientRequest extends RpcClientRequest {
  static final Setter<GrpcClientRequest, String> SETTER =
    new Setter<GrpcClientRequest, String>() { // retrolambda no like
      @Override public void put(GrpcClientRequest request, String key, String value) {
        request.setMetadata(key, value);
      }

      @Override public String toString() {
        return "GrpcClientRequest::setMetadata";
      }
    };

  final Map<String, Key<String>> nameToKey;
  final String fullMethodName;
  @Nullable volatile Metadata metadata; // nullable due to lifecycle of gRPC request

  GrpcClientRequest(Map<String, Key<String>> nameToKey, MethodDescriptor<?, ?> methodDescriptor) {
    if (nameToKey == null) throw new NullPointerException("nameToKey == null");
    if (methodDescriptor == null) throw new NullPointerException("methodDescriptor == null");
    this.nameToKey = nameToKey;
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

  void setMetadata(String name, String value) {
    if (name == null) throw new NullPointerException("name == null");
    if (value == null) throw new NullPointerException("value == null");

    Metadata metadata = this.metadata;
    if (metadata == null) {
      assert false : "This code should never be called when null";
      return;
    }
    Key<String> key = nameToKey.get(name);
    if (key == null) {
      assert false : "We currently don't support setting metadata except propagation fields";
      return;
    }
    metadata.removeAll(key);
    metadata.put(key, value);
  }
}
