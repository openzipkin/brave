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

import brave.propagation.Propagation.Setter;
import brave.test.propagation.PropagationSetterTest;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import java.util.Collections;
import java.util.Map;

import static brave.grpc.GrpcClientRequest.SETTER;
import static brave.grpc.TestObjects.METHOD_DESCRIPTOR;

public class GrpcClientRequestSetterTest extends PropagationSetterTest<GrpcClientRequest> {
  Map<String, Key<String>> nameToKey = GrpcPropagation.nameToKey(propagation);
  GrpcClientRequest request =
    new GrpcClientRequest(nameToKey, METHOD_DESCRIPTOR).metadata(new Metadata());

  @Override protected GrpcClientRequest request() {
    return request;
  }

  @Override protected Setter<GrpcClientRequest, String> setter() {
    return SETTER;
  }

  @Override protected Iterable<String> read(GrpcClientRequest request, String key) {
    Iterable<String> result = request.metadata.getAll(nameToKey.get(key));
    return result != null ? result : Collections.emptyList();
  }
}
