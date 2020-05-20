# brave-instrumentation
This module a redo of all major instrumentation libraries since Brave 3.
Artifacts have the naming convention "brave-instrumentation-XXX": for
example, the directory "servlet" includes the artifact "brave-instrumentation-servlet".

Here's a brief overview of what's packaged here:

* [dubbo](dubbo/README.md) - Tracing filter for RPC providers and consumers in [Apache Dubbo](http://dubbo.apache.org/en-us/)
* [dubbo-rpc](dubbo-rpc/README.md) - Tracing filter for RPC providers and consumers in [Alibaba Dubbo](http://dubbo.io/books/dubbo-user-book-en/)
* [grpc](grpc/README.md) - Tracing client and server interceptors for [grpc](github.com/grpc/grpc-java)
* [httpasyncclient](httpasyncclient/README.md) - Tracing decorator for [Apache HttpClient](https://hc.apache.org/httpcomponents-asyncclient-dev/) 4.0+
* [httpclient](httpclient/README.md) - Tracing decorator for [Apache HttpClient](http://hc.apache.org/httpcomponents-client-4.4.x/index.html) 4.3+
* [jaxrs2](jaxrs2/README.md) - Client tracing filter and span customizing resource filter for JAX-RS 2.x
* [jersey-server](jersey-server/README.md) - Tracing and span customizing application event listeners for [Jersey Server](https://jersey.github.io/documentation/latest/monitoring_tracing.html#d0e16007).
* [jms](jms/README.md) - Tracing decorators for JMS 1.1-2.01 producers, consumers and listeners.
* [kafka-clients](kafka-clients/README.md) - Tracing decorators for Kafka 0.11+ producers and consumers.
* [kafka-streams](kafka-streams/README.md) - Tracing decorator for Kafka Streams 2.0+ clients.
* [mongodb](mongodb/README.md) - Tracing MongoDB command listener
* [mysql](mysql/README.md) - Tracing MySQL statement interceptor
* [mysql6](mysql6/README.md) - Tracing MySQL v6 statement interceptor
* [mysql8](mysql8/README.md) - Tracing MySQL v8 statement interceptor
* [netty-codec-http](netty-codec-http/README.md) - Tracing handler for [Netty](http://netty.io/) 4.x http servers
* [okhttp3](okhttp3/README.md) - Tracing decorators for [OkHttp](https://github.com/square/okhttp) 3.x
* [p6spy](p6spy/README.md) - Tracing event listener for [P6Spy](https://github.com/p6spy/p6spy) (a proxy for calls to your JDBC driver)
* [servlet](servlet/README.md) - Tracing filter for Servlet 2.5+ (including Async)
* [sparkjava](sparkjava/README.md) - Tracing filters and exception handlers for [SparkJava](http://sparkjava.com/)
* [spring-rabbit](spring-rabbit/README.md) - Tracing MessagePostProcessor and ListenerAdvice for [Spring Rabbit](https://spring.io/guides/gs/messaging-rabbitmq/)
* [spring-web](spring-web/README.md) - Tracing interceptor for [Spring RestTemplate](https://spring.io/guides/gs/consuming-rest/)
* [spring-webmvc](spring-webmvc/README.md) - Tracing filter and span customizing interceptors for [Spring WebMVC](https://docs.spring.io/spring/docs/current/spring-framework-reference/html/mvc.html)
* [vertx-web](vertx-web/README.md) - Tracing routing context handler for [Vert.x Web](http://vertx.io/docs/vertx-web/js/)

Here are other tools we provide for configuring or testing instrumentation:
* [http](http/README.md) - `HttpTracing` that allows portable configuration of HTTP instrumentation
* [http-tests](http-tests/README.md) - Interop test suit that all http client and server instrumentation must pass
* [messaging](messaging/README.md) - `MessagingTracing` that allows portable configuration of messaging instrumentation
* [rpc](rpc/README.md) - `RpcTracing` that allows portable configuration of RPC instrumentation
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
to add a non-default tag for HTTP clients, you can do this:

```java
httpTracing = httpTracing.toBuilder()
    .clientRequestParser((req, context, span) -> {
      HttpClientRequestParser.DEFAULT.parse(req, context, span);
      span.tag("http.url", req.url()); // add the url in addition to defaults
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
    .serverSampler(HttpRuleSampler.newBuilder()
      .putRule(pathStartsWith("/favicon"), Sampler.NEVER_SAMPLE)
      .build())
    .build();
```

## Writing new instrumentation
We worked very hard to make writing new instrumentation easy and efficient.
Most of our built-in instrumentation are 50-100 lines of code, yet allow
flexible configuration of tags and sampling policy.

Brave includes two fundamental types to support tracing interprocess
communication `brave.Request` and `brave.Response`. These types represent the
following communication patterns, defined by the [Zipkin Api](https://zipkin.io/zipkin-api/#/default/post_spans)
and returned by `spanKind()`:

 * CLIENT
 * SERVER
 * PRODUCER
 * CONSUMER

Brave includes abstractions for the above that handle aspects including
sampling, header processing and data parsing:
 * [HTTP](http/README.md) is CLIENT/SERVER
 * [Messaging](messaging/README.md) is PRODUCER/CONSUMER
 * [RPC](rpc/README.md) is CLIENT/SERVER

You should use these abstractions instead of modeling your own. Not only are
they well thought through, but they also include tests to prevent common
mistakes. For example, [HTTP tests](http-tests) makes sure spans are modeled
consistently and uniform configuration works.

Using abstractions also helps avoid modeling gotchas that might not be
intuitive at first.

For example, a common mistake is using CLIENT kind for long lived connections
such as database pools or chat sessions. Doing so, however, can result in
confusing data, such as incorrect service diagrams or server calls being
attached to the wrong trace.

It is also a common misconception that a grouping of CLIENT spans should itself
be a CLIENT span. A logical span that serves only to group others, is a local
span (Span.kind() is unset). CLIENT spans represent a single remote request. It
is relatively common to use a local span as the parent of multiple CLIENT
requests, or to represent a single request that was served from a local cache.

Some features that already exist are subtle, yet proven with tests. It is best
to look for tests in the "features" package before deciding something doesn't
work.
* [Core feature tests](../brave/src/test/java/brave/features)
* [HTTP feature tests](http-tests/src/test/java/brave/http/features)
* [Messaging feature tests](messaging/src/test/java/brave/rpc/features)
* [RPC feature tests](rpc/src/test/java/brave/rpc/features)

If you end up in a position where you still believe you need a custom model,
please contact us before committing to it. There may be an alternative not yet
documented, you might prefer. Moreover, folks usually want support and we
cannot support every idea. It is better to understand this before you commit to
something bespoke. In short, please reach out on [gitter](https://gitter.im/openzipkin/zipkin),
as there are usually others around to help.
