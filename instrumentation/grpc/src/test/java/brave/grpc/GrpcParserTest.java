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
package brave.grpc;

import org.junit.Test;

import static brave.grpc.TestObjects.METHOD_DESCRIPTOR;
import static org.assertj.core.api.Assertions.assertThat;

public class GrpcParserTest {
  @Test public void method() {
    assertThat(GrpcParser.method(METHOD_DESCRIPTOR.getFullMethodName()))
      .isEqualTo("SayHello");
  }

  @Test public void method_malformed() {
    assertThat(GrpcParser.method("/")).isNull();
  }

  @Test public void service() {
    assertThat(GrpcParser.service(METHOD_DESCRIPTOR.getFullMethodName()))
      .isEqualTo("helloworld.Greeter");
  }

  @Test public void service_malformed() {
    assertThat(GrpcParser.service("/")).isNull();
  }
}
