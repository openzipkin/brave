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

  GreeterImpl(@Nullable GrpcTracing grpcTracing) {
    this.tracing = grpcTracing != null ? grpcTracing.tracing() : null;
  }

  @Override
  public void sayHello(HelloRequest req, StreamObserver<HelloReply> responseObserver) {
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

  @Override
  public void sayHelloWithManyReplies(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
    for (int i = 0; i < 10; i++) {
      responseObserver.onNext(HelloReply.newBuilder().setMessage("reply " + i).build());
    }
    responseObserver.onCompleted();
  }
}
