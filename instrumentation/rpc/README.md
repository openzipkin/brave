# brave-instrumentation-rpc

Most instrumentation are based on RPC communication. For this reason,
we have specialized handlers for RPC clients and servers. All of these
are configured with `RpcTracing`.

The `RpcTracing` class holds a reference to a tracing component and
sampling policy.

## Sampling Policy
The default sampling policy is to use the default (trace ID) sampler for
server and client requests.

For example, if there's a incoming request that has no trace IDs in its
headers, the sampler indicated by `Tracing.Builder.sampler` decides whether
or not to start a new trace. Once a trace is in progress, it is used for
any outgoing rpc client requests.

On the other hand, you may have rpc client requests that didn't originate
from a server. For example, you may be bootstrapping your application,
and that makes an RPC call to a system service. The default policy will
start a trace for any RPC call, even ones that didn't come from a server
request.

This allows you to declare rules based on RPC patterns. These decide
which sample rate to apply.

You can change the sampling policy by specifying it in the `RpcTracing`
component. The default implementation is `RpcRuleSampler`, which allows
you to declare rules based on rpc patterns.

Ex. Here's a sampler that traces 100 "Report" requests per second. This
doesn't start new traces for requests to the scribe service. Other
requests will use a global rate provided by the tracing component.

```java
import static brave.rpc.RpcRequestMatchers.*;

rpcTracingBuilder.serverSampler(RpcRuleSampler.newBuilder()
  .putRule(serviceEquals("scribe"), Sampler.NEVER_SAMPLE)
  .putRule(methodEquals("Report"), RateLimitingSampler.create(100))
  .build());
```

# Developing new instrumentation

Check for [instrumentation written here](../) and [Zipkin's list](https://zipkin.io/pages/tracers_instrumentation.html)
before rolling your own Rpc instrumentation! Besides documentation here,
you should look at the [core library documentation](../../brave/README.md) as it
covers topics including propagation. You may find our [feature tests](src/test/java/brave/rpc/features) helpful, too.
