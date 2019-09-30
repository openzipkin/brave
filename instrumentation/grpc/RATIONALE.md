# brave-instrumentation-grpc rationale
Please see [RPC](../rpc/RATIONALE.md) for basic rational about RPC instrumentation.

## Why don't we record exceptions thrown by `ClientCall.Listener.onClose()` or `ServerCall.close()`
`ClientCall.Listener.onClose()` or `ServerCall.close()` could throw an
exception after the corresponding call succeeded. However, we do not catch this
and add it to the span.

The reason could be more obvious if you consider the synchronous alternative:

```java
futureStub.hello().thenApply(response -> throw new IllegalStateException("I'm a bad user callback"))
// vs
response = stub.hello();
throw new IllegalStateException("I'm a bad user code");
```

In short, the reason we don't fail the CLIENT or SERVER span is that it its
success or failure is independent of the 3rd party (possibly user) callbacks.

This doesn't mean it won't be an error recorded in the trace, either! We have
no insight to layers over gRPC, which themselves could be instrumented and
record the application exception.

In short, we choose to not mask the gRPC status with a potential application
exception on close. In worst case, if there is no instrumentation for the layer
that throws, its trace and span ID could be known via log correlation.

## Why don't we use RpcClientHandler or RpcServerHandler for unexpected errors?
Some callbacks, such as `onHalfClose()` can throw due to unexpected bugs. In
these cases, there will be no response `Status` or `Trailers`. In some cases,
we won't have a reference to the call either. Ex when an error raises from
`ServerCallHandler.startCall`. In short the data available is sparse, and often
only the `Throwable`.

Rather than the complicate response parsing by making all fields `@Nullable`,
we decided to leave interceptor and listener bugs special case. When these
occur, we instead call `span.error(error).finish()`.
