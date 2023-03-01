# brave-instrumentation-dubbo
This is a tracing filter for RPC providers and consumers in
[Apache Dubbo3+](http://dubbo.apache.org/en-us/docs/dev/impls/filter.html)

When used on a consumer, `TracingFilter` adds trace state as attachments
to outgoing requests. When a provider, it extracts trace state from
incoming requests. In either case, the filter reports to Zipkin how long
each request took, along with any error information.

Note: A Dubbo Provider is a server, and a Dubbo Consumer is a client in
Zipkin terminology.

## Configuration
The filter "tracing" requires an extension of type `brave.rpc.RpcTracing` named
"rpcTracing" configured. Once that's configured, you assign the filter to your
providers and consumers like so:

Here's an example of with Spring 2.5+ [XML](http://dubbo.apache.org/en-us/docs/user/references/xml/dubbo-consumer.html)
```xml
<!-- default to trace all services -->
<dubbo:consumer filter="tracing" />
<dubbo:provider filter="tracing" />
```

Here's an example with dubbo.properties:
```properties
dubbo.provider.filter=tracing
dubbo.consumer.filter=tracing
```

## Sampling and data policy

Please read the [RPC documentation](../rpc/README.md) before proceeding, as it
covers important topics such as which tags are added to spans, and how traces
are sampled.

### RPC model mapping

As mentioned above, the RPC model types `RpcRequest` and `RpcResponse` allow
portable sampling decisions and tag parsing.

Dubbo maps to this model as follows:
* `RpcRequest.service()` - `Invoker.url.serviceInterface`
  * Ex. "GreeterService" for a URL "dubbo://localhost:9090?interface=brave.dubbo3.GreeterService"
* `RpcRequest.method()` - `Invocation.methodName`
  * When absent, this falls back to the string arg[0] to the "$invoke" or "$invokeAsync" methods.
* `RpcResponse.errorCode()` - The constant name for `RpcException.code`.
  * Ex. "FORBIDDEN_EXCEPTION" when `RpcException.code == 4`

### Dubbo-specific model

The `DubboRequest` and `DubboResponse` are available for custom sampling and
tag parsing.

Here is an example that adds default tags, and if Dubbo, Java arguments:
```java
rpcTracing = rpcTracingBuilder
  .clientRequestParser((req, context, span) -> {
     RpcRequestParser.DEFAULT.parse(req, context, span);
     if (req instanceof DubboRequest) {
       tagArguments(((DubboRequest) req).invocation().getArguments());
     }
  }).build();
```
