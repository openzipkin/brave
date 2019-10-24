# brave-instrumentation-rpc

This is a helper for RPC libraries such as gRPC and Dubbo. Specifically, this
includes samplers for clients and servers, configured with `RpcTracing`.

The `RpcTracing` class holds a reference to a tracing component and
sampling policy.

## Sampling Policy
The default sampling policy is to use the default (trace ID) sampler for
client and server requests.

For example, if there's a incoming request that has no trace IDs in its
headers, the sampler indicated by `RpcTracing.Builder.serverSampler`
decides whether or not to start a new trace. Once a trace is in progress, it is
used for any outgoing messages (client requests).

On the other hand, you may have outgoing requests didn't originate from a
server. For example, bootstrapping your application might call a discovery
service. In this case, the policy defined by `RpcTracing.Builder.clientSampler`
decides if a new trace will be started or not.

You can change the sampling policy by specifying it in the `RpcTracing`
component. The default implementation `RpcRuleSampler` allows you to
declare rules based on declare rules based on RPC properties and apply an
appropriate sampling rate.

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
