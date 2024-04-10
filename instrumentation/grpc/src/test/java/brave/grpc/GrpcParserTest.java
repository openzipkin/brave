/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.grpc;

import org.junit.jupiter.api.Test;

import static brave.grpc.TestObjects.METHOD_DESCRIPTOR;
import static org.assertj.core.api.Assertions.assertThat;

class GrpcParserTest {
  @Test void method() {
    assertThat(GrpcParser.method(METHOD_DESCRIPTOR.getFullMethodName()))
      .isEqualTo("SayHello");
  }

  @Test void method_malformed() {
    assertThat(GrpcParser.method("/")).isNull();
  }

  @Test void service() {
    assertThat(GrpcParser.service(METHOD_DESCRIPTOR.getFullMethodName()))
      .isEqualTo("helloworld.Greeter");
  }

  @Test void service_malformed() {
    assertThat(GrpcParser.service("/")).isNull();
  }
}
