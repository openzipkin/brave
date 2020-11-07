# grpc12
This tests that GrpcTracing does not require gRPC >1.2

Note: this uses manually generated protoc sources to remove a compile dependency on protoc.
protoc used gRPC <1.9 did not include aarch64 support, which is something that should be irrelevant
in Java code generators (because Java is platform independent). Also, gRPC 1.9 breaks grpc-trace-bin
propagation, which is something we otherwise want to test. The easiest way out was to check-in
generated sources that match the floor gRPC version 1.2.
