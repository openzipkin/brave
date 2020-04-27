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
package brave.dubbo;

import brave.internal.Nullable;
import org.apache.dubbo.rpc.Result;

/**
 * Used to access Dubbo specific aspects of a client or server response.
 *
 * <p>Here's an example that adds default tags, and if Dubbo, the Java result:
 * <pre>{@code
 * rpcTracing = rpcTracingBuilder
 *   .clientResponseParser((res, context, span) -> {
 *      RpcResponseParser.DEFAULT.parse(res, context, span);
 *      if (res instanceof DubboResponse) {
 *        DubboResponse dubboResponse = (DubboResponse) res;
 *        if (res.result() != null) {
 *          tagJavaResult(res.result().value());
 *        }
 *      }
 *   }).build();
 * }</pre>
 *
 * @since 5.12
 */
interface DubboResponse { // TODO: make public after #999
  DubboRequest request();

  @Nullable Result result();
}
