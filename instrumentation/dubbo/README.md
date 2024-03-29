# brave-instrumentation-dubbo
This is a tracing filter for RPC providers and consumers in Apache Dubbo3

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

Here's an example with dubbo.properties:
```properties
dubbo.provider.filter=tracing
dubbo.consumer.filter=tracing
```

### Registering the `brave.rpc.RpcTracing` extension with Spring
Most typically, the `brave.rpc.RpcTracing` extension is provided by Spring, so
have this in place before proceeding. The bean must be named "rpcTracing"

Here's an example in [XML](../../spring-beans/README.md).

### Registering the `brave.rpc.RpcTracing` extension with Java
Dubbo supports custom extensions. You can supply your own instance of
tracing by creating and registering an extension factory:

#### create an extension factory that returns `brave.rpc.RpcTracing`

```java
package com.yourcompany.dubbo;

import brave.Tracing;
import brave.rpc.RpcRuleSampler;
import brave.rpc.RpcTracing;
import brave.sampler.Sampler;
import org.apache.dubbo.common.extension.ExtensionInjector;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.brave.ZipkinSpanHandler;

import static brave.rpc.RpcRequestMatchers.methodEquals;

public class TracingExtensionFactory implements ExtensionInjector {

  @Override
  public <T> T getInstance(Class<T> type, String name) {
    if (type != RpcTracing.class)
      return null;

    return (T) RpcTracing.newBuilder(tracing())
      .serverSampler(serverSampler())
      .build();
  }

  RpcRuleSampler serverSampler() {
    return RpcRuleSampler.newBuilder()
      .putRule(methodEquals("sayHello"), Sampler.NEVER_SAMPLE)
      .build();
  }

  Tracing tracing() {
    return Tracing.newBuilder()
      .localServiceName("my-service")
      .addSpanHandler(spanHandler())
      .build();
  }


  // NOTE: When async, the spanHandler should be closed with a shutdown hook
  ZipkinSpanHandler spanHandler() {
    //   (this dependency is io.zipkin.reporter2:zipkin-reporter-brave)
    return ZipkinSpanHandler.create(AsyncReporter.builder(sender()));
    --snip--
```

#### Register that factory using META-INF
Make sure the following line is in `META-INF/dubbo/org.apache.dubbo.common.extension.ExtensionFactory` in your classpath:
```
tracing=com.yourcompany.dubbo.TracingExtensionFactory
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
  * Ex. "GreeterService" for a URL "dubbo://localhost:9090?interface=brave.dubbo.GreeterService"
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
