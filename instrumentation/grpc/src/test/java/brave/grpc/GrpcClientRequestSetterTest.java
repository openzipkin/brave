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

import brave.propagation.Propagation;
import brave.test.propagation.PropagationSetterTest;
import io.grpc.Metadata;
import java.util.Collections;

import static brave.grpc.GrpcClientRequest.SETTER;
import static brave.grpc.TestObjects.METHOD_DESCRIPTOR;

public class GrpcClientRequestSetterTest
  extends PropagationSetterTest<GrpcClientRequest, Metadata.Key<String>> {
  GrpcClientRequest request = new GrpcClientRequest(METHOD_DESCRIPTOR).metadata(new Metadata());

  @Override public AsciiMetadataKeyFactory keyFactory() {
    return AsciiMetadataKeyFactory.INSTANCE;
  }

  @Override protected GrpcClientRequest request() {
    return request;
  }

  @Override protected Propagation.Setter<GrpcClientRequest, Metadata.Key<String>> setter() {
    return SETTER;
  }

  @Override protected Iterable<String> read(GrpcClientRequest request, Metadata.Key<String> key) {
    Iterable<String> result = request.metadata.getAll(key);
    return result != null ? result : Collections.emptyList();
  }
}
