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

import java.io.IOException;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DubboParserTest {
  @Mock Invocation invocation;
  @Mock Invoker invoker;
  @Mock URL url;

  @Test public void method() {
    when(invocation.getMethodName()).thenReturn("sayHello");

    assertThat(DubboParser.method(invocation))
      .isEqualTo("sayHello");
  }

  @Test public void method_malformed() {
    when(invocation.getMethodName()).thenReturn("");

    assertThat(DubboParser.method(invocation)).isNull();
  }

  @Test public void method_invoke() {
    when(invocation.getMethodName()).thenReturn("$invoke");
    when(invocation.getArguments()).thenReturn(new Object[] {"sayHello"});

    assertThat(DubboParser.method(invocation))
      .isEqualTo("sayHello");
  }

  @Test public void method_invoke_nullArgs() {
    when(invocation.getMethodName()).thenReturn("$invoke");

    assertThat(DubboParser.method(invocation)).isNull();
  }

  @Test public void method_invoke_emptyArgs() {
    when(invocation.getMethodName()).thenReturn("$invoke");
    when(invocation.getArguments()).thenReturn(new Object[0]);

    assertThat(DubboParser.method(invocation)).isNull();
  }

  @Test public void method_invoke_nonStringArg() {
    when(invocation.getMethodName()).thenReturn("$invoke");
    when(invocation.getArguments()).thenReturn(new Object[] {new Object()});

    assertThat(DubboParser.method(invocation)).isNull();
  }

  @Test public void service() {
    when(invocation.getInvoker()).thenReturn(invoker);
    when(invoker.getUrl()).thenReturn(url);
    when(url.getServiceInterface()).thenReturn("brave.dubbo.GreeterService");

    assertThat(DubboParser.service(invocation))
      .isEqualTo("brave.dubbo.GreeterService");
  }

  @Test public void service_nullInvoker() {
    assertThat(DubboParser.service(invocation)).isNull();
  }

  @Test public void service_nullUrl() {
    when(invocation.getInvoker()).thenReturn(invoker);

    assertThat(DubboParser.service(invocation)).isNull();
  }

  @Test public void service_nullServiceInterface() {
    when(invocation.getInvoker()).thenReturn(invoker);
    when(invoker.getUrl()).thenReturn(url);

    assertThat(DubboParser.service(invocation)).isNull();
  }

  @Test public void service_malformed() {
    when(invocation.getInvoker()).thenReturn(invoker);
    when(invoker.getUrl()).thenReturn(url);
    when(url.getServiceInterface()).thenReturn("");

    assertThat(DubboParser.service(invocation)).isNull();
  }

  @Test public void errorCodes() {
    assertThat(DubboParser.errorCode(null))
      .isEqualTo(DubboParser.errorCode(new IOException("timeout")))
      .isNull();

    // Prove that we don't map codes to human readable names defined in RpcException
    for (int i = 0; i < 8; i++) {
      assertThat(DubboParser.errorCode(new RpcException(i)))
        .isEqualTo(String.valueOf(i));
    }
  }
}
