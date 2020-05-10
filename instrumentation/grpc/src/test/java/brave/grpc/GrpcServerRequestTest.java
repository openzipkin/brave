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
package brave.grpc;

import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import org.junit.Test;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GrpcServerRequestTest {
  Key<String> b3Key = Key.of("b3", Metadata.ASCII_STRING_MARSHALLER);
  MethodDescriptor<?, ?> methodDescriptor = TestObjects.METHOD_DESCRIPTOR;
  ServerCall call = mock(ServerCall.class);
  Metadata headers = new Metadata();
  GrpcServerRequest request =
    new GrpcServerRequest(singletonMap("b3", b3Key), call, headers);

  @Test public void service() {
    when(call.getMethodDescriptor()).thenReturn(methodDescriptor);

    assertThat(request.service()).isEqualTo("helloworld.Greeter");
  }

  @Test public void method() {
    when(call.getMethodDescriptor()).thenReturn(methodDescriptor);

    assertThat(request.service()).isEqualTo("helloworld.Greeter");
  }

  @Test public void unwrap() {
    assertThat(request.unwrap()).isSameAs(call);
  }

  @Test public void call() {
    assertThat(request.call()).isSameAs(call);
  }

  @Test public void propagationField() {
    headers.put(b3Key, "1");

    assertThat(request.propagationField("b3")).isEqualTo("1");
  }

  @Test public void propagationField_null() {
    assertThat(request.propagationField("b3")).isNull();
  }

  @Test public void propagationField_lastValue() {
    headers.put(b3Key, "0");
    headers.put(b3Key, "1");

    assertThat(request.propagationField("b3")).isEqualTo("1");
  }
}
