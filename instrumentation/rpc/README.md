# brave-instrumentation-rpc

Most instrumentation are based on RPC communication. For this reason,
we have specialized handlers for RPC clients and servers. All of these
are configured with `RpcTracing`.

The `RpcTracing` class holds a reference to a tracing component,
instructions on what to put into RPC spans, and sampling policy.

## Span data policy
By default, the following are added to both RPC client and server spans:
* Span.name is the RPC service/method. Ex. "zipkin.proto3.SpanService/Report"
  * If the service is absent, the method is the name and visa versa.
* Tags:
  * "rpc.method", eg "Report"
  * "rpc.service", eg "zipkin.proto3.SpanService"
  * "rpc.error_code", eg "CANCELLED"
  * "error" the RPC error code if there is no exception
* Remote IP and port information

Naming and tags are configurable in a library-agnostic way. For example,
the same `RpcTracing` component configures gRPC or Dubbo identically.

For example, to add a framework-specific tag for RPC clients, you can do this:

```java
rpcTracing = rpcTracingBuilder
  .clientRequestParser((req, context, span) -> {
    RpcRequestParser.DEFAULT.parse(req, context, span);
    if (req instanceof DubboRequest) {
      tagArguments(((DubboRequest) req).invocation().getArguments());
    }
  }).build();

// gRPC would silently ignore the DubboRequest parsing
grpc = GrpcTracing.create(rpcTracing);
dubbo = DubboTracing.create(rpcTracing);
```

*Note*: Data including the span name can be overwritten any time. For example,
if you don't know a good span name until the response, it is fine to replace it
then.

## Sampling Policy
The default sampling policy is to use the default (trace ID) sampler for
client and server requests.

For example, if there's a incoming request that has no trace IDs in its
headers, the sampler indicated by `Tracing.Builder.sampler` decides whether
or not to start a new trace. Once a trace is in progress, it is used for
any outgoing RPC client requests.

On the other hand, you may have RPC client requests that didn't originate
from a server. For example, you may be bootstrapping your application,
and that makes an RPC call to a system service. The default policy will
start a trace for any RPC call, even ones that didn't come from a server
request.

This allows you to declare rules based on RPC patterns. These decide
which sample rate to apply.

You can change the sampling policy by specifying it in the `RpcTracing`
component. The default implementation is `RpcRuleSampler`, which allows
you to declare rules based on RPC properties.

Ex. Here's a sampler that traces 100 "Report" requests per second. This
doesn't start new traces for requests to the "scribe" service. Other
requests will use a global rate provided by the tracing component.

```java
import static brave.rpc.RpcRequestMatchers.*;

rpcTracingBuilder.serverSampler(RpcRuleSampler.newBuilder()
  .putRule(serviceEquals("scribe"), Sampler.NEVER_SAMPLE)
  .putRule(methodEquals("Report"), RateLimitingSampler.create(100))
  .build());
```

# Developing new instrumentation

Check for [instrumentation written here](../) and [Zipkin's list](https://zipkin.io/pages/existing_instrumentations.html)
before rolling your own Rpc instrumentation! Besides documentation here,
you should look at the [core library documentation](../../brave/README.md) as it
covers topics including propagation. You may find our [feature tests](src/test/java/brave/rpc/features) helpful, too.

## Rpc Client

The first step in developing RPC client instrumentation is implementing
`RpcClientRequest` and `RpcClientResponse` for your native library.
This ensures users can portably control tags using `RpcClientParser`.

Next, you'll need to indicate how to insert trace IDs into the outgoing
request. Often, this is as simple as `Request::setHeader`.

With these two items, you now have the most important parts needed to
trace your server library. You'll likely initialize the following in a
constructor like so:
```java
MyTracingFilter(RpcTracing rpcTracing) {
  tracer = rpcTracing.tracing().tracer();
  handler = RpcClientHandler.create(rpcTracing);
}
```

### Synchronous Interceptors

Synchronous interception is the most straight forward instrumentation.
You generally need to...
1. Start the span and add trace headers to the request
2. Put the span in scope so things like log integration works
3. Invoke the request
4. If there was a Throwable, add it to the span
5. Complete the span

```java
RpcClientRequestWrapper requestWrapper = new RpcClientRequestWrapper(request);
Span span = handler.handleSend(requestWrapper); // 1.
ClientResponse response = null;
Throwable error = null;
try (Scope ws = currentTraceContext.newScope(span.context())) { // 2.
  return response = invoke(request); // 3.
} catch (Throwable e) {
  error = e; // 4.
  throw e;
} finally {
  RpcClientResponseWrapper responseWrapper =
    ? new RpcClientResponseWrapper(requestWrapper, response, error);
  handler.handleReceive(responseWrapper, span); // 5.
}
```

### Asynchronous callbacks

Asynchronous callbacks are a bit more complicated as they can happen on
different threads. This means you need to manually carry the trace context from
where the RPC call is scheduled until when the request actually starts.

You generally need to...
1. Stash the invoking trace context as a property of the request
2. Retrieve that context when the request starts
3. Use that context when creating the client span

```java
public void onSchedule(RpcContext context) {
  TraceContext invocationContext = currentTraceContext().get();
  context.setAttribute(TraceContext.class, invocationContext); // 1.
}

// use the invocation context in callback associated with starting the request
public void onStart(RpcContext context, RpcClientRequest req) {
  TraceContext parent = context.getAttribute(TraceContext.class); // 2.

  RpcClientRequestWrapper request = new RpcClientRequestWrapper(req);
  Span span = handler.handleSendWithParent(request, parent); // 3.
```

## Rpc Server

The first step in developing RPC server instrumentation is implementing
`brave.RpcServerRequest` and `brave.RpcServerResponse` for your native
library. This ensures your instrumentation can extract headers, sample and
control tags.

With these two implemented, you have the most important parts needed to trace
your server library. Initialize the RPC server handler that uses the request
and response types along with the tracer.

```java
MyTracingInterceptor(RpcTracing rpcTracing) {
  tracer = rpcTracing.tracing().tracer();
  handler = RpcServerHandler.create(rpcTracing);
}
```

### Synchronous Interceptors

Synchronous interception is the most straight forward instrumentation.
You generally need to...
1. Extract any trace IDs from headers and start the span
2. Put the span in scope so things like log integration works
3. Process the request
4. If there was a Throwable, add it to the span
5. Complete the span

```java
RpcServerRequestWrapper requestWrapper = new RpcServerRequestWrapper(request);
Span span = handler.handleReceive(requestWrapper); // 1.
ServerResponse response = null;
Throwable error = null;
try (Scope ws = currentTraceContext.newScope(span.context())) { // 2.
  return response = process(request); // 3.
} catch (Throwable e) {
  error = e; // 4.
  throw e;
} finally {
  RpcServerResponseWrapper responseWrapper =
    new RpcServerResponseWrapper(requestWrapper, response, error);
  handler.handleSend(responseWrapper, span); // 5.
}
```
