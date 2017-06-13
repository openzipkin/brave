# brave-instrumentation-jaxrs2
This module contains JAX-RS 2.x compatible tracing filters and a feature
to automatically configure them.

The `TracingClientFilter` adds trace headers to outgoing requests.
`TracingContainerFilter` extracts trace state from incoming requests.
Both report to Zipkin how long each request takes, along with relevant
tags like the http url.

## Configuration

### Dependency Injection
Tracing always needs an instance of type `brave.http.HttpTracing`
configured. Make sure this is in place before proceeding.

Then, configure `TracingFeature`, which sets up filters automatically.

For example, this is how configuration looks with Jersey 2.x:


In your web.xml:

```xml
<init-param>
    <param-name>jersey.config.server.provider.packages</param-name>
    <param-value>my.existing.packages,brave.jaxrs2</param-value>
</init-param>
```

### Manual

For server side setup, an Application class could look like this:

```java
public class MyApplication extends Application {

  public Set<Object> getSingletons() {
    Tracing tracing = Tracing.newBuilder().build();
    TracingFeature tracingFeature = TracingFeature.create(tracing);
    return new LinkedHashSet<>(Arrays.asList(tracingFeature));
  }

}
```

For client side setup, you just have to register the BraveTracingFeature
with your `WebTarget` before you make your request:

```java
WebTarget target = target("/mytarget");
target.register(TracingFeature.create(tracing));
```

## Customizing Span data based on Java Annotations
JAX-RS resources are declared via annotations. You may want to consider
the resource method when choosing name or tags.

For example, the below adds the java method name as the span name:
```java
TracingFeature.create(httpTracing.toBuilder().serverParser(new HttpServerParser() {
  @Override public <Req> String spanName(HttpAdapter<Req, ?> adapter, Req req) {
    Method method = ((ContainerAdapter) adapter).resourceMethod(req);
    return method.getName().toLowerCase();
  }
}).build());
```

## Exception handling
`ContainerResponseFilter` has no means to handle uncaught exceptions.
Unless you provide a catch-all exception mapper, requests that result in
unhandled exceptions will leak until they are eventually flushed.

Besides using framework-specific code, the only approach is to make a
custom `ExceptionMapper`, similar to below, which ensures a request is
sent back even when something unexpected happens.

```java
@Provider
public static class CatchAllExceptions implements ExceptionMapper<Exception> {

  @Override public Response toResponse(Exception e) {
    if (e instanceof WebApplicationException) {
      return ((WebApplicationException) e).getResponse();
    }

    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity("Internal error")
        .type("text/plain")
        .build();
  }
}
```

### Async Tasks

Different frameworks require different means to control the thread pool
asynchronous tasks operate on. Here's an example of how to configure
Jersey 2+ to ensure traces propagate even when requests are completed
asynchronously.

```java

@ClientAsyncExecutor
class TracingExecutorServiceProvider implements ExecutorServiceProvider {

  final ExecutorService service;

  TracingExecutorServiceProvider(Tracing tracing, ExecutorService toWrap) {
    this.service = tracing.currentTraceContext().executorService(toWrap);
  }

  @Override public ExecutorService getExecutorService() {
    return service;
  }
  --snip--
}

// when you setup your client, register this provider when registering tracing
ClientConfig clientConfig = new ClientConfig();
clientConfig.register(TracingFeature.create(httpTracing));
clientConfig.register(new TracingExecutorServiceProvider(httpTracing.tracing(), executorService));
```
