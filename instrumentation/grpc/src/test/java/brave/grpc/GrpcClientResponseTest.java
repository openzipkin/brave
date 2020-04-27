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
import io.grpc.Status;
import org.junit.Test;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class GrpcClientResponseTest {
  Key<String> b3Key = Key.of("b3", Metadata.ASCII_STRING_MARSHALLER);
  MethodDescriptor<?, ?> methodDescriptor = TestObjects.METHOD_DESCRIPTOR;
  CallOptions callOptions = CallOptions.DEFAULT;
  ClientCall<?, ?> call = mock(ClientCall.class);
  Metadata headers = new Metadata();
  GrpcClientRequest request =
    new GrpcClientRequest(singletonMap("b3", b3Key), methodDescriptor, callOptions, call, headers);
  Status status = Status.CANCELLED;
  Metadata trailers = new Metadata();
  Throwable error;
  GrpcClientResponse response = new GrpcClientResponse(request, status, trailers, error);

  @Test public void request() {
    assertThat(response.request()).isSameAs(request);
  }

  @Test public void status() {
    assertThat(response.status()).isSameAs(status);
  }

  @Test public void unwrap() {
    assertThat(response.unwrap()).isSameAs(status);
  }

  @Test public void trailers() {
    assertThat(response.trailers()).isSameAs(trailers);
  }

  @Test public void error_null() {
    assertThat(response.error()).isNull();
  }

  @Test public void error() {
    RuntimeException error = new RuntimeException("noodles");
    GrpcClientResponse response = new GrpcClientResponse(request, status, trailers, error);

    assertThat(response.error()).isSameAs(error);
  }

  @Test public void error_fromStatus() {
    RuntimeException error = new RuntimeException("noodles");
    status = Status.fromThrowable(error);
    GrpcClientResponse response = new GrpcClientResponse(request, status, trailers, null);

    assertThat(response.error()).isSameAs(error);
    assertThat(response.errorCode()).isEqualTo("UNKNOWN");
  }

  @Test public void errorCode_nullWhenOk() {
    status = Status.OK;
    GrpcClientResponse response = new GrpcClientResponse(request, status, trailers, null);

    assertThat(response.errorCode()).isNull();
  }

  @Test public void errorCode() {
    assertThat(response.errorCode()).isEqualTo("CANCELLED");
  }
}
