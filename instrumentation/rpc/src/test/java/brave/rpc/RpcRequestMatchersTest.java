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
package brave.rpc;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static brave.rpc.RpcRequestMatchers.methodEquals;
import static brave.rpc.RpcRequestMatchers.serviceEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RpcRequestMatchersTest {
  @Mock RpcRequest request;

  @Test public void methodEquals_matched() {
    when(request.method()).thenReturn("Check");

    assertThat(methodEquals("Check").matches(request)).isTrue();
  }

  @Test public void methodEquals_unmatched_mixedCase() {
    when(request.method()).thenReturn("Check");

    assertThat(methodEquals("check").matches(request)).isFalse();
  }

  @Test public void methodEquals_unmatched() {
    when(request.method()).thenReturn("Log");

    assertThat(methodEquals("Check").matches(request)).isFalse();
  }

  @Test public void methodEquals_unmatched_null() {
    assertThat(methodEquals("Check").matches(request)).isFalse();
  }

  @Test public void serviceEquals_matched() {
    when(request.service()).thenReturn("grpc.health.v1.Health");

    assertThat(serviceEquals("grpc.health.v1.Health").matches(request)).isTrue();
  }

  @Test public void serviceEquals_unmatched_mixedCase() {
    when(request.service()).thenReturn("grpc.health.v1.Health");

    assertThat(serviceEquals("grpc.health.v1.health").matches(request)).isFalse();
  }

  @Test public void serviceEquals_unmatched() {
    when(request.service()).thenReturn("scribe");

    assertThat(serviceEquals("grpc.health.v1.Health").matches(request)).isFalse();
  }

  @Test public void serviceEquals_unmatched_null() {
    assertThat(serviceEquals("grpc.health.v1.Health").matches(request)).isFalse();
  }
}
