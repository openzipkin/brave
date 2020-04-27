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

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DubboClientRequestTest {
  Invoker invoker = mock(Invoker.class);
  Invocation invocation = mock(Invocation.class);
  URL url = mock(URL.class);
  Map<String, String> attachments = new LinkedHashMap<>();
  DubboClientRequest request = new DubboClientRequest(invoker, invocation, attachments);

  @Test public void service() {
    when(invocation.getInvoker()).thenReturn(invoker);
    when(invoker.getUrl()).thenReturn(url);
    when(url.getServiceInterface()).thenReturn("brave.dubbo.GreeterService");

    assertThat(request.service())
      .isEqualTo("brave.dubbo.GreeterService");
  }

  @Test public void method() {
    when(invocation.getMethodName()).thenReturn("sayHello");

    assertThat(request.method()).isEqualTo("sayHello");
  }

  @Test public void unwrap() {
    assertThat(request.unwrap()).isSameAs(invocation);
  }

  @Test public void invoker() {
    assertThat(request.invoker()).isSameAs(invoker);
  }

  @Test public void invocation() {
    assertThat(request.invocation()).isSameAs(invocation);
  }

  @Test public void propagationField() {
    request.propagationField("b3", "d");

    assertThat(attachments).containsEntry("b3", "d");
  }
}
