# brave-instrumentation-jersey-server
This module contains an application event listener for [Jersey Server 2.x](https://jersey.github.io/documentation/latest/monitoring_tracing.html#d0e16007).

`TracingApplicationEventListener` extracts trace state from incoming
requests. Then, it reports Zipkin how long each request takes, along
with relevant tags like the http url.

To enable tracing, you need to register the `TracingApplicationEventListener`.

## Configuration

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

The tracing listener should be the only server instrumentation you use.
For example, do *not* use brave-instrumentation-jaxrs2 also, as this will
result in duplicate data.



