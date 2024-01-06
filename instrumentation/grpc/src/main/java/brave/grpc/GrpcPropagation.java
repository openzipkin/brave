/*
 * Copyright 2013-2024 The OpenZipkin Authors
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

import brave.baggage.BaggagePropagation;
import brave.propagation.Propagation;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import java.util.LinkedHashMap;
import java.util.Map;

final class GrpcPropagation {
  /** Creates constant keys for use in propagating trace identifiers or baggage. */
  static Map<String, Key<String>> nameToKey(Propagation<String> propagation) {
    Map<String, Key<String>> result = new LinkedHashMap<String, Key<String>>();
    for (String keyName : propagation.keys()) {
      result.put(keyName, Key.of(keyName, Metadata.ASCII_STRING_MARSHALLER));
    }
    for (String keyName : BaggagePropagation.allKeyNames(propagation)) {
      result.put(keyName, Key.of(keyName, Metadata.ASCII_STRING_MARSHALLER));
    }
    return result;
  }
}
