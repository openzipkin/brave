# brave-instrumentation-dubbo-rpc
This is a tracing filter for RPC providers and consumers in [Dubbo](http://dubbo.io/books/dubbo-dev-book-en/impls/filter.html)

When used on a consumer, `TracingFilter` adds trace state as attachments
to outgoing requests. When a provider, it extracts trace state from
incoming requests. In either case, the filter reports to Zipkin how long
each request took, along with any error information.

Note: A Dubbo Provider is a server, and a Dubbo Consumer is a client in
Zipkin terminology.

## Configuration

The filter "tracing" requires an extension of type `Tracing` configured.
Most typically, this is provided by Spring, so have this in place before
proceeding. Here's an example in [XML](../../spring-beans/README.md).

Then, configure the filter named "tracing" in your producer or consumer.

Here's an example of using Spring 2.5+ XML
```xml
<!-- a provider -->
<dubbo:service filter="tracing" interface="com.alibaba.dubbo.demo.DemoService" ref="demoService"/>

<!-- or a consumer -->
<dubbo:reference filter="tracing" id="demoService" check="false" interface="com.alibaba.dubbo.demo.DemoService"/>
```

