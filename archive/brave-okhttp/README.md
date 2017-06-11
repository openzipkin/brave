# brave-okhttp #
This is an [OkHttp](https://github.com/square/okhttp) interceptor which
traces client requests. It should be applied as both an application
interceptor and as a network interceptor.

The implementation models the application request as a local span. Each
network request will be a child span. For example, if there's a redirect,
there will be one span for the application request and two child spans
for the associated network requests.

Trace identifiers of each network attempt are propagated to the server
via headers prefixed with `X-B3`. These spans are also reported out of
band, with `cs` (client sent) and `cr` (client receive) annotations,
binary annotations (tags) like `http.url`, and `sa` including the
server's ip and port.

### Configuration
Since this interceptor creates nested spans, you should use nesting-aware
span state like `InheritableServerClientAndLocalSpanState`. If using
asynchronous calls, you must also wrap the dispatcher's executor
service. Regardless, the interceptor must be registered as both an
application and network interceptor. 

Here's how to add tracing to OkHttp:
```java
brave = new Brave.Builder(new InheritableServerClientAndLocalSpanState(localEndpoint))..

// The request dispatcher uses an executor service.. wrap it!
tracePropagatingExecutor = new BraveExecutorService(
    new Dispatcher().executorService(),
    brave.serverSpanThreadBinder()
);

client = new OkHttpClient.Builder()
  .addInterceptor(tracingInterceptor)
  .addNetworkInterceptor(tracingInterceptor)
  .dispatcher(new Dispatcher(tracePropagatingExecutor));
  .build();
```

### Customizing span data
The span associated with the application request is by default named
according to the request tag, falling back to the http method name.

Ex.
```java
// This request's span name will be "get"
request = new Request.Builder()
    .url("https://myhost.com/gummybears").build();

// This request's span name will be "get-gummy-bears"
request = new Request.Builder()
    .url("https://myhost.com/gummybears")
    .tag("get-gummy-bears").build();
```

If you want to change span names or tags, you can override the default
parser.
```java
tracingInterceptor = BraveTracingInterceptor.builder(brave)
  ...
  .parser(new MyOkHttpParser()).build();
```

Be careful when customizing, particularly not to add too much data.
Larges span (ex large orders of kilobytes) can be problematic and/or
dropped. Also, be careful that span names have low cardinality (ex no
embedded variables). Finally, prefer names in `zipkin.TraceKeys` where
possible, so that lookup keys are coherent.

For more information, look at our
[instrumentation docs](http://zipkin.io/pages/instrumenting.html)

### Naming the server
When calling an service that isn't traced with Zipkin, such as a cloud
service. You'll want to assign a server name so that it shows up in
Zipkin's service dependency graph. You can set this via
`BraveTracingInterceptor.Builder.serverName()`

```java
tracingInterceptor = BraveTracingInterceptor.builder(brave)
  ...
  .serverName("github").build();

request = new Request.Builder()
    .url("https://api.github.com/repos/square/okhttp/issues")
    ...
```

