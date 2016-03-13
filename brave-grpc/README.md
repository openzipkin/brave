# brave-grpc #

Contains a client and server interceptor for [grpc-java](github.com/grpc/grpc-java) that integrates Brave with gRPC.

To enable tracing for a gRPC application, add the interceptors when when constructing the client and server bindings:

```java
    Server server = ServerBuilder.forPort(serverPort)
        .addService(ServerInterceptors.intercept(
            GreeterGrpc.bindService(new GreeterImpl()), new BraveGrpcServerInterceptor(brave)))
        .build()
        .start();
        
    ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", serverPort)
        .intercept(new BraveGrpcClientInterceptor("helloCallingService", brave))
        .usePlaintext(true)
        .build();
```

## Development

If you are working on this module, then you need to run `mvn install` to first compile the protos. Once the protos are compiled, then can be found in the directories:

`
./target/generated-sources/protobuf/grpc-java
./target/generated-sources/protobuf/java
`

In order to have them discovered by IntellJ, go to `File -> Project Structure`, select the `brave-grpc` module, and then add the above directories as sources
