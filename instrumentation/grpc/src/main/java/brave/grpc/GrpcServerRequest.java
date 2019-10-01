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

// intentionally not yet public until we add tag parsing functionality
final class GrpcServerRequest extends RpcServerRequest {
  static final Getter<GrpcServerRequest, Metadata.Key<String>> GETTER =
    new Getter<GrpcServerRequest, Metadata.Key<String>>() { // retrolambda no like
      @Override public String get(GrpcServerRequest request, Metadata.Key<String> key) {
        return request.metadata(key);
      }

      @Override public String toString() {
        return "Metadata::get";
      }
    };

  final String fullMethodName;
  final Metadata metadata;

  GrpcServerRequest(String fullMethodName, Metadata metadata) {
    if (fullMethodName == null) throw new NullPointerException("fullMethodName == null");
    this.fullMethodName = fullMethodName;
    if (metadata == null) throw new NullPointerException("metadata == null");
    this.metadata = metadata;
  }

  @Override public Object unwrap() {
    return this;
  }

  @Override public String method() {
    int index = fullMethodName.lastIndexOf('/');
    if (index == -1) return null;
    return fullMethodName.substring(index);
  }

  @Override public String service() {
    int index = fullMethodName.lastIndexOf('/');
    if (index == -1) return null;
    return fullMethodName.substring(0, index);
  }

  @Nullable <T> T metadata(Metadata.Key<T> key) {
    return metadata.get(key);
  }
}
