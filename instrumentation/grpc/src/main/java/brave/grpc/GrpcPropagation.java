/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
