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

### Http tracing
Most instrumentation are based on http communication. For this reason,
we have specialized handlers for http clients and servers. All of these
are configured with `HttpTracing`.

The `HttpTracing` class holds a reference to a tracing component and also
includes instructions on what to put into http spans.

By default, the following is added for both http clients and servers:
* Span.name as the http method in lowercase: ex "get"
* Tags/binary annotations:
  * "http.path", which does not include query parameters.
  * "http.status_code" when the status us not success.
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
    .serverName("remote-service") // assume both libraries are calling the same service
    .build();

apache = TracingHttpClientBuilder.create(httpTracing).build();
okhttp = TracingCallFactory.create(httpTracing, new OkHttpClient());
```