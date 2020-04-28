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

## Message processing

## Message processing callback context
If you need to process messages, streaming or otherwise, you can use normal
gRPC interceptors. The current span will be the following, regardless of
message count per request (streaming):

* `ClientInterceptor`
  * `ClientCall.sendMessage()` - the client trace context
  * `ClientCall.Listener.onMessage()` - the invocation trace context (aka parent)
  Why is discussed in the main [instrumentation rationale](../RATIONALE.md).
* `ServerInterceptor`
  * `ServerCall.Listener.onMessage()` - the server trace context
  * `ServerCall.sendMessage()` - the server trace context
  Both sides are in the server context, as otherwise would be a new trace.

Ex. To annotate the time each message sent to a server, you can do this:
```java
SpanCustomizer span = CurrentSpanCustomizer.create(tracing);

// Make sure this is ordered before the tracing interceptor! Otherwise, it will
// not see the current span.
managedChannelBuilder.intercept(new ClientInterceptor() {
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
    MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
    return new SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
      public void sendMessage(ReqT message) {
        span.annotate("grpc.send_message");
        delegate().sendMessage(message);
      }
    };
  }
}, grpcTracing.newClientInterceptor());
```

## gRPC Propagation Format (Census interop)

gRPC defines a [binary encoded propagation format](https://github.com/census-instrumentation/opencensus-specs/blob/master/encodings/BinaryEncoding.md) which is implemented
by [OpenCensus](https://opencensus.io/) instrumentation. When this is
the case, incoming requests will have two metadata keys "grpc-trace-bin"
and "grpc-tags-bin".

When enabled, this component can extract trace contexts from these
metadata and also write the same keys on outgoing calls. This allows
transparent interop when both census and brave report data to the same
tracing system.

To enable this feature, set `grpcPropagationFormatEnabled` which is off
by default:
```java
grpcTracing = GrpcTracing.newBuilder(tracing)
                         .grpcPropagationFormatEnabled(true).build();
```

Warning: the format of both "grpc-trace-bin" and "grpc-tags-bin" are
version 0. As such, consider this feature experimental.

## Development

If you are working on this module, then you need to run `mvn install` to first compile the protos. Once the protos are compiled, then can be found in the directories:

`
./target/generated-test-sources/protobuf/grpc-java
./target/generated-test-sources/protobuf/java
`

You may need to add the above directories as test sources in your IDE.
