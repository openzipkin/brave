# brave-instrumentation-http

Most instrumentation are based on http communication. For this reason,
we have specialized handlers for http clients and servers. All of these
are configured with `HttpTracing`.

The `HttpTracing` class holds a reference to a tracing component,
instructions on what to put into http spans, and sampling policy.

## Span data policy
By default, the following are added to both http client and server spans:
* Span.name is the http method in lowercase: ex "get" or a route described below
* Tags/binary annotations:
  * "http.method", eg "GET"
  * "http.path", which does not include query parameters.
  * "http.status_code" when the status is not success.
  * "error", when there is an exception or status is >=400
* Remote IP and port information

A route based name looks like "delete /users/{userId}", "post not_found"
or "get redirected". There's a longer section on Http Route later.

Naming and tags are configurable in a library-agnostic way. For example,
the same `HttpTracing` component configures OkHttp or Apache HttpClient
identically.

For example, to change the tagging policy for clients, you can do
something like this:

```java
httpTracing = httpTracing.toBuilder()
    .clientParser(new HttpClientParser() {
      @Override
      public <Req> void request(HttpAdapter<Req, ?> adapter, Req req, SpanCustomizer customizer) {
        customizer.name(spanName(adapter, req)); // default span name
        customizer.tag("http.url", adapter.url(req)); // the whole url, not just the path
      }
    })
    .build();

apache = TracingHttpClientBuilder.create(httpTracing.clientOf("s3"));
okhttp = TracingCallFactory.create(httpTracing.clientOf("sqs"), new OkHttpClient());
```

If you just want to control span naming policy based on the request,
override `spanName` in your client or server parser.

Ex:
```java
overrideSpanName = new HttpClientParser() {
  @Override public <Req> String spanName(HttpAdapter<Req, ?> adapter, Req req) {
    // If using JAX-RS, maybe we want to use the resource method
    if (adapter instanceof ContainerAdapter) {
      Method method = ((ContainerAdapter) adapter).resourceMethod(req);
      return method.getName().toLowerCase();
    }
    // If not using framework-specific knowledge, we can use http
    // attributes or go with the default.
    return super.spanName(adapter, req);
  }
};
```

Note that span name can be overwritten any time, for example, when
parsing the response, which is the case when route-based names are used.

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

## Http Route
The http route is an expression such as `/items/:itemId` representing an
application endpoint. `HttpAdapter.route()` parses this from a response,
returning the route that matched the request, empty if no route matched,
or null if routes aren't supported. This value is either used to create
a tag "http.route" or as an input to a span naming function.

### Http route cardinality
The http route groups similar requests together, so results in limited
cardinality, often a better choice for a span name vs the http method.

For example, the route `/users/{userId}`, matches `/users/25f4c31d` and
`/users/e3c553be`. If a span name function used the http path instead,
it could DOS-style attack vector on your span name index, as it would
grow unbounded vs `/users/{userId}`. Even if different frameworks use
different formats, such as `/users/[0-9a-f]+` or `/users/:userId`, the
cardinality is still fixed with regards to request count.

The http route can be "" (empty) on redirect or not-found. If you use
http route for metrics, coerce empty to constants like "redirected" or
"not_found" with the http status. Knowing the difference between not
found and redirected can be a simple intrusion detection signal. The
default span name policy uses constants when a route isn't known for
reasons including sharing the span name as a metrics correlation field.

# Developing new instrumentation

Check for [instrumentation written here](../) and [Zipkin's list](https://zipkin.io/pages/existing_instrumentations.html)
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

### Supporting HttpAdapter.route(response)

Although the route is associated with the request, not the response,
it is parsed from the response object. The reason is that many server
implementations process the request before they can identify the route.

Instrumentation authors implement support via extending HttpAdapter
accordingly. There are a few patterns which might help.

#### Callback with non-final type
When a framework uses an callback model, you are in control of the type
being parsed. If the response type isn't final, simply subclass it with
the route data.

For example, if Spring MVC, it would be this:
```java
    // check for a wrapper type which holds the template
    handler = HttpServerHandler.create(httpTracing, new HttpServletAdapter() {
      @Override public String template(HttpServletResponse response) {
        return response instanceof HttpServletResponseWithTemplate
            ? ((HttpServletResponseWithTemplate) response).route : null;
      }

      @Override public String toString() {
        return "WebMVCAdapter{}";
      }
    });

--snip--

// when parsing the response, scope the route from the request

    Object template = request.getAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (route != null) {
      response = new HttpServletResponseWithTemplate(response, route.toString());
    }
    handler.handleSend(response, ex, span);
```

#### Callback with final type
If the response type is final, you may be able to make a copy and stash
the route as a synthetic header. Since this is a copy of the response,
there's no chance a user will receive this header in a real response.

Here's an example for Play, where the header "brave-http-route" holds
the route temporarily until the parser can read it.
```scala
    result.onComplete {
      case Failure(t) => handler.handleSend(null, t, span)
      case Success(r) => {
        // add a synthetic header if there was a routing path set
        var resp = template.map(t => r.withHeaders("brave-http-route" -> t)).getOrElse(r)
        handler.handleSend(resp, null, span)
      }
    }
--snip--
    override def route(response: Result): String =
      response.header.headers.apply("brave-http-route")
```

#### Common mistakes

For grouping to work, we want routes that are effectively the same, to
in fact be the same. Here are a couple things on that.

* Always start with a leading slash
  * This allows you to differentiate the root path from empty (no route)
  * This prevents accidental partitioning like `users/:userId` from `/users/:userId`
* Take care not to duplicate slashes
  * When joining nested paths, avoid writing templates like `/nested//users/:userId`
  * The `ITHttpServer` test will catch some of this
