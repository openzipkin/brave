# brave-context-grpc
This manages trace scope using `io.grpc.Context` as opposed to a thread
local.

To enable this, configure `brave.Tracing` with `GrpcCurrentTraceContext`
like so:

```java
tracing = Tracing.newBuilder()
    .currentTraceContext(GrpcCurrentTraceContext.create())
    ...
    .build();
```
