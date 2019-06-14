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

import brave.propagation.Propagation;
import brave.test.propagation.PropagationSetterTest;
import io.grpc.Metadata;
import java.util.Collections;

import static brave.grpc.TracingClientInterceptor.SETTER;

public class MetadataSetterTest extends PropagationSetterTest<Metadata, Metadata.Key<String>> {
  Metadata carrier = new Metadata();

  @Override public AsciiMetadataKeyFactory keyFactory() {
    return AsciiMetadataKeyFactory.INSTANCE;
  }

  @Override protected Metadata carrier() {
    return carrier;
  }

  @Override protected Propagation.Setter<Metadata, Metadata.Key<String>> setter() {
    return SETTER;
  }

  @Override protected Iterable<String> read(Metadata carrier, Metadata.Key<String> key) {
    Iterable<String> result = carrier.getAll(key);
    return result != null ? result : Collections.emptyList();
  }
}
