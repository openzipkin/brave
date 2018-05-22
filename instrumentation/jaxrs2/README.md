# brave-instrumentation-jaxrs2
This module contains JAX-RS 2.x compatible tracing filters.

`SpanCustomizingContainerFilter` layers over [servlet](../servlet),
adding resource tags to servlet-originated spans.

If you are using Jersey, use our [jersey-server](../jersey-server)
instrumentation instead of `SpanCustomizingContainerFilter` as it has
more data.

## Container Setup
If not using [jersey-server](../jersey-server), setup [servlet tracing](../servlet),
then add `SpanCustomizingContainerFilter`.

Ex.
```java
public class MyApplication extends Application {

  public Set<Object> getSingletons() {
    return new LinkedHashSet<>(Arrays.asList(
      SpanCustomizingContainerFilter.create(),
      .. your other things
    ));
  }

}
```

## Customizing Span data based on resources
`ContainerParser` decides which resource-specific (data beyond normal
http tags) end up on the span. You can override this to change what's
parsed, or use `NOOP` to disable controller-specific data.

Ex. If you want less tags, you can disable the JAX-RS resource ones.
```java
SpanCustomizingContainerFilter.create(ContainerParser.NOOP);
```

## Client Setup

For client side setup, you just have to register the TracingClientFilter
with your `WebTarget` before you make your request:

```java
WebTarget target = target("/mytarget");
target.register(TracingClientFilter.create(tracing));
```

### Async Tasks
Different frameworks require different means to control the thread pool
asynchronous tasks operate on.

Here's an example setting up the executor used by Jersey:
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

## Why don't we use `ContainerResponseFilter`

### `ContainerResponseFilter` doesn't handle errors
`ContainerResponseFilter` has no means to handle uncaught exceptions.
Unless you provide a catch-all exception mapper, requests that result in
unhandled exceptions will leak until they are eventually flushed. This
problem does not exist in servlet or Jersey-specific instrumentation.

### Async Tasks can lose context
Different frameworks require different means to control the thread pool
asynchronous tasks operate on. This implies using servlet or framework-
specific mechanisms.
