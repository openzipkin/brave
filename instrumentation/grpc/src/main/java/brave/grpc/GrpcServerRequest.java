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
import brave.propagation.Propagation.Getter;
import brave.rpc.RpcServerRequest;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.MethodDescriptor;
import java.util.Map;

// intentionally not yet public until we add tag parsing functionality
final class GrpcServerRequest extends RpcServerRequest {
  static final Getter<GrpcServerRequest, String> GETTER =
    new Getter<GrpcServerRequest, String>() { // retrolambda no like
      @Override public String get(GrpcServerRequest request, String key) {
        return request.getMetadata(key);
      }

      @Override public String toString() {
        return "GrpcServerRequest::getMetadata";
      }
    };

  final Map<String, Key<String>> nameToKey;
  final String fullMethodName;
  final Metadata metadata;

  GrpcServerRequest(
    Map<String, Key<String>> nameToKey,
    MethodDescriptor<?, ?> methodDescriptor,
    Metadata metadata
  ) {
    if (nameToKey == null) throw new NullPointerException("nameToKey == null");
    if (methodDescriptor == null) throw new NullPointerException("methodDescriptor == null");
    if (metadata == null) throw new NullPointerException("metadata == null");
    this.nameToKey = nameToKey;
    this.fullMethodName = methodDescriptor.getFullMethodName();
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

  @Nullable String getMetadata(String name) {
    if (name == null) throw new NullPointerException("name == null");
    Key<String> key = nameToKey.get(name);
    if (key == null) {
      assert false : "We currently don't support getting metadata except propagation fields";
      return null;
    }
    return metadata.get(key);
  }
}
