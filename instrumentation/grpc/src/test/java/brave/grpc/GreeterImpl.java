package brave.grpc;

import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.stub.StreamObserver;

class GreeterImpl extends GreeterGrpc.GreeterImplBase {
  static final HelloRequest HELLO_REQUEST = HelloRequest.newBuilder()
      .setName("tracer")
      .build();

  @Override public void sayHello(HelloRequest req, StreamObserver<HelloReply> responseObserver) {
    if (req.getName().equals("bad")) {
      responseObserver.onError(new IllegalArgumentException());
      return;
    }
    HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + req.getName()).build();
    responseObserver.onNext(reply);
    responseObserver.onCompleted();
  }
}
