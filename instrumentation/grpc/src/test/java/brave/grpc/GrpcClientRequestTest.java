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

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.MethodDescriptor;
import org.junit.Test;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class GrpcClientRequestTest {
  Key<String> b3Key = Key.of("b3", Metadata.ASCII_STRING_MARSHALLER);
  MethodDescriptor<?, ?> methodDescriptor = TestObjects.METHOD_DESCRIPTOR;
  CallOptions callOptions = CallOptions.DEFAULT;
  ClientCall<?, ?> call = mock(ClientCall.class);
  Metadata headers = new Metadata();
  GrpcClientRequest request =
    new GrpcClientRequest(singletonMap("b3", b3Key), methodDescriptor, callOptions, call, headers);

  @Test public void service() {
    assertThat(request.service()).isEqualTo("helloworld.Greeter");
  }

  @Test public void method() {
    assertThat(request.method()).isEqualTo("SayHello");
  }

  @Test public void unwrap() {
    assertThat(request.unwrap()).isSameAs(call);
  }

  @Test public void methodDescriptor() {
    assertThat(request.methodDescriptor()).isSameAs(methodDescriptor);
  }

  @Test public void callOptions() {
    assertThat(request.callOptions()).isSameAs(callOptions);
  }

  @Test public void call() {
    assertThat(request.call()).isSameAs(call);
  }

  @Test public void propagationField() {
    request.propagationField("b3", "d");

    assertThat(headers.get(b3Key)).isEqualTo("d");
  }

  @Test public void propagationField_replace() {
    headers.put(b3Key, "0");

    request.propagationField("b3", "1");

    assertThat(request.headers.get(b3Key)).isEqualTo("1");
  }

  @Test public void propagationField_null() {
    assertThat(request.headers.get(b3Key)).isNull();
  }
}
