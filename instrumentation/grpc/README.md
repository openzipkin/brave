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
grpcTracing = GrpcTracing.create(tracing);

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

## Span data policy
By default, the following are added to both gRPC client and server spans:
* Span.name as the full method name: ex "helloworld.greeter/sayhello"
* Tags/binary annotations:
  * "grpc.status_code" when the status is not OK.
  * "error", when there is an exception or status is not OK.

Ex. To add a tag corresponding to a message sent to a server, you can do
something like this:

```java
grpcTracing = grpcTracing.toBuilder()
    .clientParser(new GrpcClientParser() {
      @Override protected <M> void onMessageSent(M message, SpanCustomizer span) {
        span.tag("grpc.message_sent", message.toString());
      }
    })
    .build();
```

If you just want to control span naming policy, override `spanName` in
your client or server parser.

Ex:
```java
overrideSpanName = new GrpcClientParser() {
  @Override protected <ReqT, RespT> String spanName(MethodDescriptor<ReqT, RespT> methodDescriptor) {
    return methodDescriptor.getType().name();
  }
};
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
