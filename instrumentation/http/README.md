# brave-instrumentation-http

Most instrumentation are based on http communication. For this reason,
we have specialized handlers for http clients and servers. All of these
are configured with `HttpTracing`.

The `HttpTracing` class holds a reference to a tracing component,
instructions on what to put into http spans, and sampling policy.

## Span data policy
By default, the following are added to both http client and server spans:
* Span.name as the http method in lowercase: ex "get"
* Tags/binary annotations:
  * "http.path", which does not include query parameters.
  * "http.status_code" when the status us not success.
  * "error", when there is an exception or status is >=400
* Remote IP and port information

Naming and tags are configurable in a library-agnostic way. For example,
the same `HttpTracing` component configures OkHttp or Apache HttpClient
identically.

For example, to change the span and tag naming policy for clients, you
can do something like this:

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

apache = TracingHttpClientBuilder.create(httpTracing.clientOf("s3"));
okhttp = TracingCallFactory.create(httpTracing.clientOf("sqs"), new OkHttpClient());
```

If you just want to control span naming policy, override `spanName` in
your client or server parser.

Ex:
```java
overrideSpanName = new HttpClientParser() {
  @Override public <Req> String spanName(HttpAdapter<Req, ?> adapter, Req req) {
    return adapter.method(req).toLowerCase() + " " + adapter.path(req);
  }
};
```

## Sampling Policy
The default sampling policy is to use the default (trace ID) sampler for
server and client requests.

For example, if there's a incoming request that has no trace IDs in its
headers, the sampler indicated by `Tracing.Builder.sampler` decides whether
or not to start a new trace. Once a trace is in progress, it is used for
any outgoing http client requests.

On the other hand, you may have http client requests that didn't originate
from a server. For example, you may be bootstrapping your application,
and that makes an http call to a system service. The default policy will
start a trace for any http call, even ones that didn't come from a server
request.

This allows you to declare rules based on http patterns. These decide
which sample rate to apply.

You can change the sampling policy by specifying it in the `HttpTracing`
component. The default implementation is `HttpRuleSampler`, which allows
you to declare rules based on http patterns.

Ex. Here's a sampler that traces 80% requests to /foo and 10% of POST
requests to /bar. This doesn't start new traces for requests to favicon
(which many browsers automatically fetch). Other requests will use a
global rate provided by the tracing component.

```java
httpTracingBuilder.serverSampler(HttpRuleSampler.newBuilder()
  .addRule(null, "/favicon", 0.0f)
  .addRule(null, "/foo", 0.8f)
  .addRule("POST", "/bar", 0.1f)
  .build());
```

# Developing new instrumentation

Check for [instrumentation written here](../instrumentation/) and [Zipkin's list](http://zipkin.io/pages/existing_instrumentations.html)
before rolling your own Http instrumentation! Besides documentation here,
you should look at the [core library documentation](../../brave/README.md) as it
covers topics including propagation. You may find our [feature tests](src/test/java/brave/http/features) helpful, too.

## Http Client

The first step in developing http client instrumentation is implementing
a `HttpClientAdapter` for your native library. This ensures users can
portably control tags using `HttpClientParser`.

Next, you'll need to indicate how to insert trace IDs into the outgoing
request. Often, this is as simple as `Request::setHeader`.

With these two items, you now have the most important parts needed to
trace your server library. You'll likely initialize the following in a
constructor like so:
```java
MyTracingFilter(HttpTracing httpTracing) {
  tracer = httpTracing.tracing().tracer();
  handler = HttpClientHandler.create(httpTracing, new MyHttpClientAdapter());
  extractor = httpTracing.tracing().propagation().injector(Request::setHeader);
}
```

### Synchronous Interceptors

Synchronous interception is the most straight forward instrumentation.
You generally need to...
1. Start the span and add trace headers to the request
2. Put the span in scope so things like log integration works
3. Invoke the request
4. Catch any errors
5. Complete the span

```java
Span span = handler.handleSend(injector, request); // 1.
Throwable error = null;
try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) { // 2.
  response = invoke(request); // 3.
} catch (RuntimeException | Error e) {
  error = e; // 4.
  throw e;
} finally {
  handler.handleReceive(response, error, span); // 5.
}
```

## Http Server

The first step in developing http server instrumentation is implementing
a `HttpServerAdapter` for your native library. This ensures users can
portably control tags using `HttpServerParser`. See [HttpServletAdapter](../servlet/src/main/java/brave/servlet/HttpServletAdapter.java)
as an example (you may even be able to use it!).

Next, you'll need to indicate how to extract trace IDs from the incoming
request. Often, this is as simple as `Request::getHeader`.

With these two items, you now have the most important parts needed to
trace your server library. You'll likely initialize the following in a
constructor like so:
```java
MyTracingInterceptor(HttpTracing httpTracing) {
  tracer = httpTracing.tracing().tracer();
  handler = HttpServerHandler.create(httpTracing, new MyHttpServerAdapter());
  extractor = httpTracing.tracing().propagation().extractor(Request::getHeader);
}
```

### Synchronous Interceptors

Synchronous interception is the most straight forward instrumentation.
You generally need to...
1. Extract any trace IDs from headers and start the span
2. Put the span in scope so things like log integration works
3. Invoke the request
4. Catch any errors
5. Complete the span

```java
Span span = handler.handleReceive(extractor, request); // 1.
Throwable error = null;
try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) { // 2.
  response = invoke(request); // 3.
} catch (RuntimeException | Error e) {
  error = e; // 4.
  throw e;
} finally {
  handler.handleSend(response, error, span); // 5.
}
```