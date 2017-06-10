package brave.grpc;

import brave.Tracing;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.stub.StreamObserver;
import javax.annotation.Nullable;

class GreeterImpl extends GreeterGrpc.GreeterImplBase {
  static final HelloRequest HELLO_REQUEST = HelloRequest.newBuilder()
      .setName("tracer")
      .build();

  @Nullable final Tracing tracing;

  GreeterImpl(@Nullable Tracing tracing) {
    this.tracing = tracing;
  }

  @Override public void sayHello(HelloRequest req, StreamObserver<HelloReply> responseObserver) {
    if (req.getName().equals("bad")) {
      responseObserver.onError(new IllegalArgumentException());
      return;
    }
    String message = tracing != null && tracing.currentTraceContext().get() != null
        ? tracing.currentTraceContext().get().traceIdString()
        : "";
    HelloReply reply = HelloReply.newBuilder().setMessage(message).build();
    responseObserver.onNext(reply);
    responseObserver.onCompleted();
  }
}
