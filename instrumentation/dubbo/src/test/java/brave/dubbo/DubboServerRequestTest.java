/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.dubbo;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DubboServerRequestTest {
  Invoker invoker = mock(Invoker.class);
  Invocation invocation = mock(Invocation.class);
  URL url = URL.valueOf("dubbo://localhost:6666?scope=remote&interface=brave.dubbo.GreeterService");
  DubboServerRequest request = new DubboServerRequest(invoker, invocation);

  @Test void service() {
    when(invocation.getInvoker()).thenReturn(invoker);
    when(invoker.getUrl()).thenReturn(url);

    assertThat(request.service())
        .isEqualTo("brave.dubbo.GreeterService");
  }

  @Test void method() {
    when(invocation.getMethodName()).thenReturn("sayHello");

    assertThat(request.method()).isEqualTo("sayHello");
  }

  @Test void unwrap() {
    assertThat(request.unwrap()).isSameAs(invocation);
  }

  @Test void invoker() {
    assertThat(request.invoker()).isSameAs(invoker);
  }

  @Test void invocation() {
    assertThat(request.invocation()).isSameAs(invocation);
  }

  @Test void propagationField() {
    when(invocation.getAttachment("b3")).thenReturn("d");

    assertThat(request.propagationField("b3")).isEqualTo("d");
  }
}
