# brave-instrumentation
This module a redo of all major instrumentation libraries since Brave 3.
Artifacts have the naming convention "brave-instrumentation-XXX": for
example, the directory "servlet" includes the artifact "brave-instrumentation-servlet".

Notably, this adds a more `HttpTracing` component to configure http client
and server tracing. It also adds support for async servlet and apache http
client, and tests edge cases like multiple servlet versions. Finally, it
tests and benchmarks every http component, so that people are aware of
overhead is involved in tracing.

Here's an example of configuring OkHttp. Note that all instrumentation
have the same naming policy TracingXXX, where XXX is usually the same
as the type returned.

```java
tracing = Tracing.newBuilder()
                 .localServiceName("my-service")
                 .reporter(reporter)
                 .build();
httpTracing = HttpTracing.newBuilder(tracing).serverName("github").build();
okhttp = TracingCallFactory.create(httpTracing, new OkHttpClient());
```

# Http tracing
Most instrumentation are based on http communication. For this reason,
we have specialized handlers for http clients and servers. All of these
are configured with `HttpTracing`.

The `HttpTracing` class holds a reference to a tracing component,
instructions on what to put into http spans, and sampling policy.

## Tagging policy
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
      @Override public <Req> String spanName(HttpAdapter<Req, ?> adapter, Req req) {
        return adapter.method(req).toLowerCase() + " " + adapter.path(req);
      }

      @Override
      public <Req> void requestTags(HttpAdapter<Req, ?> adapter, Req req, brave.Span span) {
        span.tag(TraceKeys.HTTP_URL, adapter.url(req)); // the whole url, not just the path
      }
    })
    .build();

apache = TracingHttpClientBuilder.create(httpTracing.clientOf("s3"));
okhttp = TracingCallFactory.create(httpTracing.clientOf("sqs"), new OkHttpClient());
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

You can change the sampling policy by specifying it in the `HttpTracing`
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

# Developing new instrumentation

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
portably control tags using `HttpServerParser`. See [HttpServletAdapter](./servlet/src/main/java/brave/servlet/HttpServletAdapter.java)
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