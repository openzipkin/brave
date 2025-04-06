# brave-instrumentation-jersey-server
This module contains application event listeners for [Jersey Server 2.x](https://jersey.github.io/documentation/latest/monitoring_tracing.html#d0e16007).

These instrumentation is an alternative to the [jaxrs2](../jaxrs2) container
instrumentation. Do *not* use both.

`TracingApplicationEventListener` extracts trace state from incoming
requests. Then, it reports Zipkin how long each request takes, along
with relevant tags like the http url and the resource.

`SpanCustomizingApplicationEventListener` layers over [servlet](../servlet),
adding resource tags and route information to servlet-originated spans.

When in a servlet environment, use `SpanCustomizingApplicationEventListener`.
When not, use `TracingApplicationEventListener`. Don't use both!

`TracingApplicationEventListener` extracts trace state from incoming
requests. Then, it reports Zipkin how long each request takes, along
with relevant tags like the http url.

To enable tracing, you need to register the `TracingApplicationEventListener`.

## Configuration

### Normal configuration (`TracingApplicationEventListener`)

The `TracingApplicationEventListener` requires an instance of
`HttpTracing` to operate. With that in mind, use [standard means](https://jersey.github.io/apidocs/2.26/jersey/org/glassfish/jersey/server/monitoring/ApplicationEventListener.html)
to register the listener.

For example, you could wire up like this:
```java
public class MyApplication extends Application {

  public Set<Object> getSingletons() {
    HttpTracing httpTracing = // configure me!
    return new LinkedHashSet<>(Arrays.asList(
      TracingApplicationEventListener.create(httpTracing),
      new MyResource()
    ));
  }
}
```

### Servlet-based configuration (`SpanCustomizingApplicationEventListener`)

When using `jersey-container-servlet`, setup [servlet tracing](../servlet),
an register `SpanCustomizingContainerFilter`.

```java
public class MyApplication extends Application {
  public Set<Object> getSingletons() {
    HttpTracing httpTracing = // configure me!
    return new LinkedHashSet<>(Arrays.asList(
      SpanCustomizingApplicationEventListener.create(),
      new MyResource()
    ));
  }
```


## Customizing Span data based on resources
`EventParser` decides which resource-specific (data beyond normal
http tags) end up on the span. You can override this to change what's
parsed, or use `NOOP` to disable controller-specific data.

Ex. If you want less tags, you can disable the JAX-RS resource ones.
```java
SpanCustomizingApplicationEventListener.create(EventParser.NOOP);
```
