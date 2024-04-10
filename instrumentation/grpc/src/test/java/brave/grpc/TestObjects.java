/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
