/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rpc;

import brave.propagation.Propagation.Setter;
import brave.test.propagation.PropagationSetterTest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static brave.rpc.RpcClientRequest.SETTER;

public class RpcClientRequestSetterTest extends PropagationSetterTest<RpcClientRequest> {
  Map<String, String> propagationFields = new LinkedHashMap<>();

  @Override protected RpcClientRequest request() {
    return new RpcClientRequest() {
      @Override public Object unwrap() {
        return null;
      }

      @Override public String method() {
        return null;
      }

      @Override public String service() {
        return null;
      }

      @Override protected void propagationField(String keyName, String value) {
        propagationFields.put(keyName, value);
      }
    };
  }

  @Override protected Setter<RpcClientRequest, String> setter() {
    return SETTER;
  }

  @Override protected Iterable<String> read(RpcClientRequest request, String key) {
    String result = propagationFields.get(key);
    return result != null ? Collections.singletonList(result) : Collections.emptyList();
  }
}
