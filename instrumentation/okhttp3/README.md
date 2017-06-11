# brave-instrumentation-okhttp3
This module contains a tracing decorators for [OkHttp](https://github.com/square/okhttp) 3.x.

## TracingCallFactory
`TracingCallFactory` adds trace headers to outgoing requests. It
then reports to Zipkin how long each request takes, along with relevant
tags like the http url.

To enable tracing, wrap your client using `TracingCallFactory`.

```java
callFactory = TracingCallFactory.create(tracing, okhttp);
```

## TracingInterceptor
Sometimes code must use `OkHttpClient`, not `Call.Factory`. When this is
the case, you can add the network interceptor `TracingInterceptor`. Make
sure you wrap the dispatcher's executor service.

```java
new OkHttpClient.Builder()
        .dispatcher(new Dispatcher(
            httpTracing.tracing().currentTraceContext()
                .executorService(new Dispatcher().executorService())
        ))
        .addNetworkInterceptor(TracingInterceptor.create(httpTracing))
        .build()
```

Note that when the keep-alive pool is full (backlog situation), this
approach can result in broken traces. This limitation is not the case
when using the call factory approach.