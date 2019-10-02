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
package brave.dubbo.rpc;

import brave.internal.Nullable;
import brave.propagation.Propagation.Getter;
import brave.rpc.RpcServerRequest;
import com.alibaba.dubbo.rpc.Invocation;
import java.util.Map;

// intentionally not yet public until we add tag parsing functionality
final class DubboServerRequest extends RpcServerRequest {
  static final Getter<DubboServerRequest, String> GETTER =
    new Getter<DubboServerRequest, String>() {
      @Override public String get(DubboServerRequest request, String key) {
        return request.getAttachment(key);
      }

      @Override public String toString() {
        return "DubboServerRequest::getAttachment";
      }
    };

  final Invocation invocation;
  final Map<String, String> attachments;

  DubboServerRequest(Invocation invocation, Map<String, String> attachments) {
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

  @Nullable String getAttachment(String key) {
    return attachments.get(key);
  }
}
