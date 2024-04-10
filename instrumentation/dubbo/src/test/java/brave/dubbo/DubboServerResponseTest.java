/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.dubbo;

import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.Test;

import static org.apache.dubbo.rpc.RpcException.TIMEOUT_EXCEPTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class DubboServerResponseTest {
  Invoker invoker = mock(Invoker.class);
  Invocation invocation = mock(Invocation.class);
  Result result = mock(Result.class);
  RpcException error = new RpcException(TIMEOUT_EXCEPTION);
  DubboServerRequest request = new DubboServerRequest(invoker, invocation);
  DubboServerResponse response = new DubboServerResponse(request, result, error);

  @Test void request() {
    assertThat(response.request()).isSameAs(request);
  }

  @Test void result() {
    assertThat(response.result()).isSameAs(result);
  }

  @Test void unwrap() {
    assertThat(response.unwrap()).isSameAs(result);
  }

  @Test void error() {
    assertThat(response.error()).isSameAs(error);
  }

  @Test void errorCode() {
    assertThat(response.errorCode()).isEqualTo("TIMEOUT_EXCEPTION");
  }
}
