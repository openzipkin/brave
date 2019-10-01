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

import brave.rpc.RpcClientRequest;

// intentionally not yet public until we add tag parsing functionality
final class GrpcClientRequest extends RpcClientRequest {
  final String fullMethodName;

  GrpcClientRequest(String fullMethodName) {
    if (fullMethodName == null) throw new NullPointerException("fullMethodName == null");
    this.fullMethodName = fullMethodName;
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
}
