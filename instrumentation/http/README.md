# brave-instrumentation-http

Most instrumentation are based on http communication. For this reason,
we have specialized handlers for http clients and servers. All of these
are configured with `HttpTracing`.

The `HttpTracing` class holds a reference to a tracing component,
instructions on what to put into http spans, and sampling policy.

## Span data policy
By default, the following are added to both http client and server spans:
* Span.name is the http method in lowercase: ex "get" or a route described below
* Tags:
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

For example, to add a non-default tag for HTTP clients, you can do this:

```java
httpTracing = httpTracing.toBuilder()
    .clientRequestParser((req, context, span) -> {
      HttpClientRequestParser.DEFAULT.parse(req, context, span);
      span.tag("http.url", req.url()); // add the url in addition to defaults
    })
    .build();

apache = TracingHttpClientBuilder.create(httpTracing.clientOf("s3"));
okhttp = TracingCallFactory.create(httpTracing.clientOf("sqs"), new OkHttpClient());
```

If you just want to control span naming policy based on the request,
override `spanName` in your client or server parser.

Ex:
```java
overrideSpanName = new HttpRequestParser.Default() {
  @Override protected String spanName(HttpRequest req, TraceContext context) {
    // If using Armeria, maybe we want to reuse the request log name
    Object raw = req.unwrap();
    if (raw instanceof ServiceRequestContext) {
      RequestLog requestLog = ((ServiceRequestContext) raw).log();
      return requestLog.name();
    }
    return super.spanName(req, context); // otherwise, go with the defaults
  }
};
```

Note that span name can be overwritten any time, for example, when
parsing the response, which is the case when route-based names are used.

This increased performance and allows easier access to extra fields. For
example, a common request was to add extra fields as tags. This can now
be done in the parser instead of the FinishedSpanHandler, if desired.

### Extra fields
To add extra fields as span tags, use the context parameter like so:

```java
httpTracing = httpTracing.toBuilder()
    .clientRequestParser((req, context, span) -> {
      HttpClientRequestParser.DEFAULT.parse(req, context, span);
      String userName = ExtraFieldPropagation.get(context, "user-name");
      if (userName != null) span.tag("user-name", userName);
    })
    .build();
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

Ex. Here's a sampler that traces 100 requests per second to /foo and 10
POST requests to /bar per second. This doesn't start new traces for
requests to favicon (which many browsers automatically fetch). Other
requests will use a global rate provided by the tracing component.

```java
httpTracingBuilder.serverSampler(HttpRuleSampler.newBuilder()
  .putRule(pathStartsWith("/favicon"), Sampler.NEVER_SAMPLE)
  .putRule(pathStartsWith("/foo"), RateLimitingSampler.create(100))
  .putRule(and(methodIsEqualTo("POST"), pathStartsWith("/bar")), RateLimitingSampler.create(10))
  .build());
```

## Http Route
The http route is an expression such as `/items/:itemId` representing an
application endpoint. Implement `HttpServerResponse.route()` to return the
route that matched the request, empty if no route matched, or null if routes
aren't supported. This value is either used to create a tag "http.route" or as
an input to a span naming function.

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
`HttpClientRequest` and `HttpClientResponse` for your native library.
This ensures users can portably control tags using `HttpClientParser`.

Next, you'll need to indicate how to insert trace IDs into the outgoing
request. Often, this is as simple as `Request::setHeader`.

With these two items, you now have the most important parts needed to
trace your server library. You'll likely initialize the following in a
constructor like so:
```java
MyTracingFilter(HttpTracing httpTracing) {
  tracer = httpTracing.tracing().tracer();
  handler = HttpClientHandler.create(httpTracing);
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
HttpClientRequestWrapper wrapper = new HttpClientRequestWrapper(request);
Span span = handler.handleSend(wrapper); // 1.
Result result = null;
Throwable error = null;
try (Scope ws = currentTraceContext.newScope(span.context())) { // 2.
  return result = invoke(request); // 3.
} catch (Throwable e) {
  error = e; // 4.
  throw e;
} finally {
  HttpClientResponseWrapper response = result != null
    ? new HttpClientResponseWrapper(wrapper, result, error)
    : null;
  handler.handleReceive(response, error, span); // 5.
}
```

### Asynchronous callbacks

Asynchronous callbacks are a bit more complicated as they can happen on
different threads. This means you need to manually carry the trace context from
where the HTTP call is scheduled until when the request actually starts.

You generally need to...
1. Stash the invoking trace context as a property of the request
2. Retrieve that context when the request starts
3. Use that context when creating the client span

```java
public void onSchedule(HttpContext context) {
  TraceContext invocationContext = currentTraceContext().get();
  context.setAttribute(TraceContext.class, invocationContext); // 1.
}

// use the invocation context in callback associated with starting the request
public void onStart(HttpContext context, HttpClientRequest req) {
  TraceContext parent = context.getAttribute(TraceContext.class); // 2.

  HttpClientRequestWrapper request = new HttpClientRequestWrapper(req);
  Span span = handler.handleSendWithParent(request, parent); // 3.
```

## Http Server

The first step in developing http server instrumentation is implementing
`brave.HttpServerRequest` and `brave.HttpServerResponse` for your native
library. This ensures your instrumentation can extract headers, sample and
control tags.

With these two implemented, you have the most important parts needed to trace
your server library. Initialize the HTTP server handler that uses the request
and response types along with the tracer.

```java
MyTracingInterceptor(HttpTracing httpTracing) {
  tracer = httpTracing.tracing().tracer();
  handler = HttpServerHandler.create(httpTracing);
}
```

### Synchronous Interceptors

Synchronous interception is the most straight forward instrumentation.
You generally need to...
1. Extract any trace IDs from headers and start the span
2. Put the span in scope so things like log integration works
3. Process the request
4. Catch any errors
5. Complete the span

```java
HttpServerRequestWrapper wrapper = new HttpServerRequestWrapper(request);
Span span = handler.handleReceive(wrapper); // 1.
Result result = null;
Throwable error = null;
try (Scope ws = currentTraceContext.newScope(span.context())) { // 2.
  return result = process(request); // 3.
} catch (RuntimeException | Error e) {
  error = e; // 4.
  throw e;
} finally {
  HttpServerResponseWrapper response = result != null
    ? new HttpServerResponseWrapper(wrapper, result, error)
    : null;
  handler.handleSend(response, error, span); // 5.
}
```

### Supporting HttpResponse.request()

`HttpResponse.request()` is request that initiated the HTTP response. Since

Implementations should return the last wire-level request that caused the
response or error. HTTP properties like path and headers might be different,
due to redirects or authentication. Some properties might not be visible until
response processing, notably the route.

For these reasons, you may need to generate a different `HttpRequest` instance
when constructing the `HttpResponse` vs when it was created.

Here is an example for Apache HttpAsyncClient, which grabs the request out
of its context as it doesn't have a reference during response processing:

```scala
static final class HttpResponseWrapper extends HttpClientResponse {
  @Nullable final HttpRequestWrapper request;
  final HttpResponse response;

  HttpResponseWrapper(HttpResponse response, HttpContext context) {
    HttpRequest request = HttpClientContext.adapt(context).getRequest();
    this.request = request != null ? new HttpRequestWrapper(request, context) : null;
    this.response = response;
  }
```

### Supporting HttpRequest.route()

The route is associated with the request, but it may not be visible until
response processing. The reasons is that many server implementations process
the request before they can identify the route.

Instrumentation authors implement support via overriding `HttpRequest.route()`
accordingly. There are a few patterns which might help.

#### Servlet based libraries
'brave-instrumentation-servlet' includes the type `HttpServletRequestWrapper`.
This looks for the request attribute "http.route", which can be set in any way.

For example, Spring WebMVC can add the route using `HandlerInterceptorAdapter`.
This is how our 'brave-instrumentation-spring-webmvc' works:

```java
static void setHttpRouteAttribute(HttpServletRequest request) {
  Object httpRoute = request.getAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE);
  request.setAttribute("http.route", httpRoute != null ? httpRoute.toString() : "");
}
```

#### Adding a field to `HttpServerRequest`
Another easy way is to add a field to your `HttpServerRequest` wrapper, and
parse that in your implementation of `HttpServerRequest.route()`.

Here is an example for Play, which passes the template along with the `Request`
to the HTTP server handler:
```scala
var template = req.attrs.get(Router.Attrs.HandlerDef).map(_.path)
var request = new RequestWrapper(req, template)

--snip--
  override def route(): String =
    template.map(t => StringUtils.replace(t, "<[^/]+>", "")).getOrElse("")
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
