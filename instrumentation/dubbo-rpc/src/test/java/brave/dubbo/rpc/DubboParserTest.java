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

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.RpcException;
import java.io.IOException;
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
    URL url = URL.valueOf("dubbo://localhost:9090?interface=brave.dubbo.GreeterService");
    when(invoker.getUrl()).thenReturn(url);

    assertThat(DubboParser.service(invoker))
        .isEqualTo("brave.dubbo.GreeterService");
  }

  @Test public void service_nullUrl() {
    assertThat(DubboParser.service(invoker)).isNull();
  }

  @Test public void service_nullServiceInterface() {
    URL url = URL.valueOf("dubbo://localhost:9090");
    when(invoker.getUrl()).thenReturn(url);

    assertThat(DubboParser.service(invoker)).isNull();
  }

  @Test public void service_malformed() {
    URL url = URL.valueOf("dubbo://localhost:9090?interface=");
    when(invoker.getUrl()).thenReturn(url);

    assertThat(DubboParser.service(invoker)).isNull();
  }

  @Test public void errorCodes() {
    assertThat(DubboParser.errorCode(null))
        .isEqualTo(DubboParser.errorCode(new IOException("timeout")))
        .isNull();

    assertThat(DubboParser.errorCode(new RpcException(0)))
        .isEqualTo("UNKNOWN_EXCEPTION");
    assertThat(DubboParser.errorCode(new RpcException(1)))
        .isEqualTo("NETWORK_EXCEPTION");
    assertThat(DubboParser.errorCode(new RpcException(2)))
        .isEqualTo("TIMEOUT_EXCEPTION");
    assertThat(DubboParser.errorCode(new RpcException(3)))
        .isEqualTo("BIZ_EXCEPTION");
    assertThat(DubboParser.errorCode(new RpcException(4)))
        .isEqualTo("FORBIDDEN_EXCEPTION");
    assertThat(DubboParser.errorCode(new RpcException(5)))
        .isEqualTo("SERIALIZATION_EXCEPTION");
    assertThat(DubboParser.errorCode(new RpcException(6)))
        .isEqualTo("6"); // this will catch drift if Dubbo adds another code
  }
}
