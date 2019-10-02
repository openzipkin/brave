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

import io.grpc.MethodDescriptor;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

final class TestObjects {
  // Allows invoker tests to run without needing to compile protos
  static final MethodDescriptor<Void, Void> METHOD_DESCRIPTOR =
    MethodDescriptor.<Void, Void>newBuilder()
      .setType(MethodDescriptor.MethodType.UNARY)
      .setFullMethodName("helloworld.Greeter/SayHello")
      .setRequestMarshaller(VoidMarshaller.INSTANCE)
      .setResponseMarshaller(VoidMarshaller.INSTANCE)
      .build();

  enum VoidMarshaller implements MethodDescriptor.Marshaller<Void> {
    INSTANCE;

    @Override public InputStream stream(Void value) {
      return new ByteArrayInputStream(new byte[0]);
    }

    @Override public Void parse(InputStream stream) {
      return null;
    }
  }
}
