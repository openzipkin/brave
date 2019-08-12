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

import brave.CurrentSpanCustomizer;
import brave.NoopSpanCustomizer;
import brave.SpanCustomizer;
import brave.Tracing;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.stub.StreamObserver;

class GreeterImpl extends GreeterGrpc.GreeterImplBase {

  static final HelloRequest HELLO_REQUEST = HelloRequest.newBuilder().setName("tracer").build();

  @Nullable final Tracing tracing;
  final SpanCustomizer spanCustomizer;

  GreeterImpl(@Nullable GrpcTracing grpcTracing) {
    tracing = grpcTracing != null ? grpcTracing.tracing : null;
    spanCustomizer =
      tracing != null ? CurrentSpanCustomizer.create(tracing) : NoopSpanCustomizer.INSTANCE;
  }

  @Override
  public void sayHello(HelloRequest req, StreamObserver<HelloReply> responseObserver) {
    TraceContext currentTraceContext = tracing != null ? tracing.currentTraceContext().get() : null;
    if (req.getName().equals("bad")) {
      responseObserver.onError(new IllegalArgumentException());
      return;
    }
    if (req.getName().equals("testerror")) {
      throw new RuntimeException("testerror");
    }
    String message = currentTraceContext != null ? currentTraceContext.traceIdString() : "";
    HelloReply reply = HelloReply.newBuilder().setMessage(message).build();
    responseObserver.onNext(reply);
    responseObserver.onCompleted();
  }

  @Override
  public void sayHelloWithManyReplies(
    HelloRequest request, StreamObserver<HelloReply> responseObserver) {
    for (int i = 0; i < 10; i++) {
      responseObserver.onNext(HelloReply.newBuilder().setMessage("reply " + i).build());
    }
    responseObserver.onCompleted();
  }
}
