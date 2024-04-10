/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rpc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static brave.rpc.RpcRequestMatchers.methodEquals;
import static brave.rpc.RpcRequestMatchers.serviceEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RpcRequestMatchersTest {
  @Mock RpcRequest request;

  @Test void methodEquals_matched() {
    when(request.method()).thenReturn("Check");

    assertThat(methodEquals("Check").matches(request)).isTrue();
  }

  @Test void methodEquals_unmatched_mixedCase() {
    when(request.method()).thenReturn("Check");

    assertThat(methodEquals("check").matches(request)).isFalse();
  }

  @Test void methodEquals_unmatched() {
    when(request.method()).thenReturn("Log");

    assertThat(methodEquals("Check").matches(request)).isFalse();
  }

  @Test void methodEquals_unmatched_null() {
    assertThat(methodEquals("Check").matches(request)).isFalse();
  }

  @Test void serviceEquals_matched() {
    when(request.service()).thenReturn("grpc.health.v1.Health");

    assertThat(serviceEquals("grpc.health.v1.Health").matches(request)).isTrue();
  }

  @Test void serviceEquals_unmatched_mixedCase() {
    when(request.service()).thenReturn("grpc.health.v1.Health");

    assertThat(serviceEquals("grpc.health.v1.health").matches(request)).isFalse();
  }

  @Test void serviceEquals_unmatched() {
    when(request.service()).thenReturn("scribe");

    assertThat(serviceEquals("grpc.health.v1.Health").matches(request)).isFalse();
  }

  @Test void serviceEquals_unmatched_null() {
    assertThat(serviceEquals("grpc.health.v1.Health").matches(request)).isFalse();
  }
}
