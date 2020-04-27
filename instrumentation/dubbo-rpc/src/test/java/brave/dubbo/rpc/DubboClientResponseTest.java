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
package brave.dubbo.rpc;

import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static com.alibaba.dubbo.rpc.RpcException.TIMEOUT_EXCEPTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class DubboClientResponseTest {
  Invoker invoker = mock(Invoker.class);
  Invocation invocation = mock(Invocation.class);
  Result result = mock(Result.class);
  RpcException error = new RpcException(TIMEOUT_EXCEPTION);
  Map<String, String> attachments = new LinkedHashMap<>();
  DubboClientRequest request = new DubboClientRequest(invoker, invocation, attachments);
  DubboClientResponse response = new DubboClientResponse(request, result, error);

  @Test public void request() {
    assertThat(response.request()).isSameAs(request);
  }

  @Test public void result() {
    assertThat(response.result()).isSameAs(result);
  }

  @Test public void unwrap() {
    assertThat(response.unwrap()).isSameAs(result);
  }

  @Test public void error() {
    assertThat(response.error()).isSameAs(error);
  }

  @Test public void errorCode() {
    assertThat(response.errorCode()).isEqualTo("TIMEOUT_EXCEPTION");
  }
}
