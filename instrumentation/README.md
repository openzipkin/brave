# brave-instrumentation
This module a redo of all major instrumentation libraries since Brave 3.
Artifacts have the naming convention "brave-instrumentation-XXX": for
example, the directory "servlet" includes the artifact "brave-instrumentation-servlet".

Here's a brief overview of what's packaged here:

* [grpc](grpc/README.md) - Tracing client and server interceptors for [grpc](github.com/grpc/grpc-java)
* [httpasyncclient](httpasyncclient/README.md) - Tracing decorator for [Apache HttpClient](https://hc.apache.org/httpcomponents-asyncclient-dev/) 4.0+
* [httpclient](httpclient/README.md) - Tracing decorator for [Apache HttpClient](http://hc.apache.org/httpcomponents-client-4.4.x/index.html) 4.3+
* [jaxrs2](jaxrs2/README.md) - Tracing filters and a feature to automatically configure them
* [mysql](mysql/README.md) - Tracing MySQL statement interceptor
* [okhttp3](okhttp3/README.md) - Tracing decorators for [OkHttp](https://github.com/square/okhttp) 3.x
* [p6spy](p6spy/README.md) - Tracing event listener for [P6Spy](https://github.com/p6spy/p6spy) (a proxy for calls to your JDBC driver)
* [servlet](servlet/README.md) - Tracing filter for Servlet 2.5+ (including Async)
* [sparkjava](sparkjava/README.md) - Tracing filters and exception handlers for [SparkJava](http://sparkjava.com/)
* [spring-web](spring-web/README.md) - Tracing interceptor for [Spring RestTemplate](https://spring.io/guides/gs/consuming-rest/)
* [spring-webmvc](spring-webmvc/README.md) - Tracing interceptor for [Spring WebMVC](https://docs.spring.io/spring/docs/current/spring-framework-reference/html/mvc.html)

Here are other tools we provide for configuring or testing instrumentation:
* [http](http/README.md) - `HttpTracing` that allows portable configuration of http instrumentation
* [http-tests](http-tests/README.md) - Interop test suit that all http client and server instrumentation must pass
* [spring-beans](../spring-beans/README.md) - This allows you to setup tracing with XML instead of custom code.
* [benchmarks](benchmarks/README.md) - JMH microbenchmarks that measure instrumentation overhead

## Configuration

### Log integration
You may want to put trace IDs into your log files, or change thread local
behavior. Look at our [context libraries](../context/), for integration with
tools such as SLF4J.

### XML Configuration
If you are trying to trace legacy applications, you may be interested in
[Spring XML Configuration](../brave-spring-beans/README.md). This allows you to setup
tracing without any custom code.

### Custom configuration
When re-using trace instrumentation, you typically do not need to write
any code. However, you can customize data and sampling policy through
common types. The `HttpTracing` type configures all libraries the same way.

Ex.
```java
apache = TracingHttpClientBuilder.create(httpTracing.clientOf("s3"));
okhttp = TracingCallFactory.create(httpTracing.clientOf("sqs"), new OkHttpClient());
```

Below introduces common configuration. See the [http instrumentation docs](http/README.md)
for more.

#### Span Data
Naming and tags are configurable in a library-agnostic way. For example,
to change the span and tag naming policy for clients, you can do this:

```java
httpTracing = httpTracing.toBuilder()
    .clientParser(new HttpClientParser() {
      @Override
      public <Req> void request(HttpAdapter<Req, ?> adapter, Req req, SpanCustomizer customizer) {
        customizer.name(adapter.method(req).toLowerCase() + " " + adapter.path(req));
        customizer.tag(TraceKeys.HTTP_URL, adapter.url(req)); // the whole url, not just the path
      }
    })
    .build();
```

#### Request-based Sampling
Which requests to start traces for is configurable in a library-agnostic
way. You can change the sampling policy by specifying it in the `HttpTracing`
component. Here's an example which doesn't start new traces for requests
to favicon (which many browsers automatically fetch).

```java
httpTracing = httpTracing.toBuilder()
    .serverSampler(new HttpSampler() {
       @Override public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request) {
         if (adapter.path(request).startsWith("/favicon")) return false;
         return null; // defer decision to probabilistic on trace ID
       }
     })
    .build();
```

## Writing new instrumentation
We worked very hard to make writing new instrumentation easy and efficient.
Most of our built-in instrumentation are 50-100 lines of code, yet allow
flexible configuration of tags and sampling policy.

If you need to write new http instrumentation, check [our docs](http/README.md),
as this shows how to write it in a way that is least effort for you and
easy for others to configure. For example, we have a standard [test suite](http-tests)
you can use to make sure things interop, and standard configuration works.

If you need to do something not http, you'll want to use our [tracer library](../brave/README.md).
If you are in this position, you may find our [feature tests](../brave/src/test/java/brave/features)
helpful.

Regardless, you may need support along the way. Please reach out on [gitter](https://gitter.im/openzipkin/zipkin),
as there's usually others around to help.
