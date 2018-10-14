# brave-instrumentation-dubbo-rpc
This is a tracing filter for RPC providers and consumers in [Dubbo 2.6+](http://dubbo.apache.org/en-us/docs/dev/impls/filter.html)

When used on a consumer, `TracingFilter` adds trace state as attachments
to outgoing requests. When a provider, it extracts trace state from
incoming requests. In either case, the filter reports to Zipkin how long
each request took, along with any error information.

Note: A Dubbo Provider is a server, and a Dubbo Consumer is a client in
Zipkin terminology.

## Configuration
The filter "tracing" requires an extension of type `brave.Tracing` named
"tracing" configured. Once that's configured, you assign the filter to
your providers and consumers like so:

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

### Registering the `brave.Tracing` extension with Spring
Most typically, the `brave.Tracing` extension is provided by Spring, so
have this in place before proceeding. The bean must be named "tracing"

Here's an example in [XML](../../spring-beans/README.md).

### Registering the `brave.Tracing` extension with Java
Dubbo supports custom extensions. You can supply your own instance of
tracing by creating and registering an extension factory:

#### create an extension factory that returns `brave.Tracing`

```java
package com.yourcompany.dubbo;

import brave.Tracing;
import com.alibaba.dubbo.common.extension.ExtensionFactory;
import zipkin2.reporter.AsyncReporter;
import zipkin2.Span;

public class TracingExtensionFactory implements ExtensionFactory {

  @Override public <T> T getExtension(Class<T> type, String name) {
    if (type != Tracing.class) return null;
    return (T) Tracing.newBuilder()
                      .localServiceName("my-service")
                      .spanReporter(spanReporter())
                      .build();
  }

  // NOTE: The reporter should be closed with a shutdown hook
  AsyncReporter<Span> spanReporter() {
--snip--
```

#### Register that factory using META-INF
Make sure the following line is in `META-INF/dubbo/com.alibaba.dubbo.common.extension.ExtensionFactory` in your classpath:
```
tracing=com.yourcompany.dubbo.TracingExtensionFactory
```
