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
package brave.dubbo;

import brave.propagation.Propagation.Setter;
import brave.rpc.RpcClientRequest;
import java.util.Map;
import org.apache.dubbo.rpc.Invocation;

// intentionally not yet public until we add tag parsing functionality
final class DubboClientRequest extends RpcClientRequest {
  static final Setter<DubboClientRequest, String> SETTER =
    new Setter<DubboClientRequest, String>() {
      @Override public void put(DubboClientRequest request, String key, String value) {
        request.putAttachment(key, value);
      }

      @Override public String toString() {
        return "DubboClientRequest::putAttachment";
      }
    };

  final Invocation invocation;
  final Map<String, String> attachments;

  DubboClientRequest(Invocation invocation, Map<String, String> attachments) {
    if (invocation == null) throw new NullPointerException("invocation == null");
    this.invocation = invocation;
    if (attachments == null) throw new NullPointerException("attachments == null");
    this.attachments = attachments;
  }

  @Override public Object unwrap() {
    return this;
  }

  @Override public String method() {
    return DubboParser.method(invocation);
  }

  @Override public String service() {
    return DubboParser.service(invocation);
  }

  String putAttachment(String key, String value) {
    return attachments.put(key, value);
  }
}
