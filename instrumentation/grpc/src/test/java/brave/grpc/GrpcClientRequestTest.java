/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.grpc;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.MethodDescriptor;
import org.junit.jupiter.api.Test;

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

  @Test void service() {
    assertThat(request.service()).isEqualTo("helloworld.Greeter");
  }

  @Test void method() {
    assertThat(request.method()).isEqualTo("SayHello");
  }

  @Test void unwrap() {
    assertThat(request.unwrap()).isSameAs(call);
  }

  @Test void methodDescriptor() {
    assertThat(request.methodDescriptor()).isSameAs(methodDescriptor);
  }

  @Test void callOptions() {
    assertThat(request.callOptions()).isSameAs(callOptions);
  }

  @Test void call() {
    assertThat(request.call()).isSameAs(call);
  }

  @Test void propagationField() {
    request.propagationField("b3", "d");

    assertThat(headers.get(b3Key)).isEqualTo("d");
  }

  @Test void propagationField_replace() {
    headers.put(b3Key, "0");

    request.propagationField("b3", "1");

    assertThat(request.headers.get(b3Key)).isEqualTo("1");
  }

  @Test void propagationField_null() {
    assertThat(request.headers.get(b3Key)).isNull();
  }
}
