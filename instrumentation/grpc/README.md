# brave-instrumentation-grpc
This contains tracing client and server interceptors for [grpc](https://github.com/grpc/grpc-java).

The `GrpcTracing` class holds a reference to a tracing component,
instructions on what to put into gRPC spans, and interceptors.

The client interceptor adds trace headers to outgoing requests. The
server interceptor extracts trace state from incoming requests. Both
report to Zipkin how long each request takes, along with relevant
tags like the response status.

To enable tracing for a gRPC application, add the interceptors when
constructing the client and server bindings:

```java
grpcTracing = GrpcTracing.create(rpcTracing);

Server server = ServerBuilder.forPort(serverPort)
    .addService(ServerInterceptors.intercept(
        GreeterGrpc.bindService(new GreeterImpl()), grpcTracing.newServerInterceptor()))
    .build()
    .start();

ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", serverPort)
    .intercept(grpcTracing.newClientInterceptor())
    .usePlaintext(true)
    .build();
```

## Sampling and data policy

Please read the [RPC documentation](../rpc/README.md) before proceeding, as it
covers important topics such as which tags are added to spans, and how traces
are sampled.

### RPC model mapping

As mentioned above, the RPC model types `RpcRequest` and `RpcResponse` allow
portable sampling decisions and tag parsing.

gRPC maps to this model as follows:
* `RpcRequest.service()` - text preceding the first slash ('/') in `MethodDescriptor.fullMethodName`
  * Ex. "helloworld.Greeter" for a full method name "helloworld.Greeter/SayHello"
* `RpcRequest.method()` - text following the first slash ('/') in `MethodDescriptor.fullMethodName`
  * Ex. "SayHello" for a full method name "helloworld.Greeter/SayHello"
* `RpcResponse.errorCode()` - `Status.Code.name()` when not `Status.isOk()`
  * Ex. `null` for `Status.Code.OK` and `UNKNOWN` for `Status.Code.UNKNOWN`

### gRPC-specific model

The `GrpcRequest` and `GrpcResponse` are available for custom sampling and tag
parsing.

Here is an example that adds default tags, and if gRPC, the method type (ex "UNARY"):
```java
Tag<GrpcRequest> methodType = new Tag<GrpcRequest>("grpc.method_type") {
  @Override protected String parseValue(GrpcRequest input, TraceContext context) {
    return input.methodDescriptor().getType().name();
  }
};

RpcRequestParser addMethodType = (req, context, span) -> {
  RpcRequestParser.DEFAULT.parse(req, context, span);
  if (req instanceof GrpcRequest) methodType.tag((GrpcRequest) req, span);
};

grpcTracing = GrpcTracing.create(RpcTracing.newBuilder(tracing)
    .clientRequestParser(addMethodType)
    .serverRequestParser(addMethodType).build());
```

## Development

If you are working on this module, then you need to run `mvn install` to first compile the protos. Once the protos are compiled, then can be found in the directories:

`
./target/generated-test-sources/protobuf/grpc-java
./target/generated-test-sources/protobuf/java
`

You may need to add the above directories as test sources in your IDE.
