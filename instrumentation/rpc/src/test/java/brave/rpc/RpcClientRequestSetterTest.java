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
