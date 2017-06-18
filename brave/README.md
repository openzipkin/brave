Brave Api (v4)
==============

Brave is a library used to capture and report latency information about
distributed operations to Zipkin. Most users won't use Brave directly,
rather libraries or frameworks than employ Brave on their behalf.

This module includes tracer creates and joins spans that model the
latency of potentially distributed work. It also includes libraries to
propagate the trace context over network boundaries, for example, via
http headers.

## Setup

Most importantly, you need a Tracer, configured to [report to Zipkin](https://github.com/openzipkin/zipkin-reporter-java).

Here's an example setup that sends trace data (spans) to Zipkin over
http (as opposed to Kafka).

```java
// Configure a reporter, which controls how often spans are sent
//   (the dependency is io.zipkin.reporter:zipkin-sender-okhttp3)
sender = OkHttpSender.create("http://127.0.0.1:9411/api/v1/spans");
reporter = AsyncReporter.create(sender);
// Create a tracing component with the service name you want to see in Zipkin.
tracing = Tracing.newBuilder()
                 .localServiceName("my-service")
                 .reporter(reporter)
                 .build();

// Tracing exposes objects you might need, most importantly the tracer
tracer = tracing.tracer();

// When all tracing tasks are complete, close the tracing component and reporter
// This might be a shutdown hook for some users
tracing.close();
reporter.close();
```

## Tracing

The tracer creates and joins spans that model the latency of potentially
distributed work. It can employ sampling to reduce overhead in process
or to reduce the amount of data sent to Zipkin.

Spans returned by a tracer report data to Zipkin when finished, or do
nothing if unsampled. After starting a span, you can annotate events of
interest or add tags containing details or lookup keys.

Spans have a context which includes trace identifiers that place it at
the correct spot in the tree representing the distributed operation.

### Local Tracing

When tracing local code, just run it inside a span.
```java
Span span = tracer.newTrace().name("encode").start();
try {
  doSomethingExpensive();
} finally {
  span.finish();
}
```

In the above example, the span is the root of the trace. In many cases,
you will be a part of an existing trace. When this is the case, call
`newChild` instead of `newTrace`

```java
Span span = tracer.newChild(root.context()).name("encode").start();
try {
  doSomethingExpensive();
} finally {
  span.finish();
}
```

### Customizing spans
Once you have a span, you can add tags to it, which can be used as lookup
keys or details. For example, you might add a tag with your runtime
version.

```java
span.tag("clnt/finagle.version", "6.36.0");
```

When exposing the ability to customize spans to third parties, prefer
`brave.SpanCustomizer` as opposed to `brave.Span`. The former is simpler to
understand and test, and doesn't tempt users with span lifecycle hooks.

```java
interface MyTraceCallback {
  void request(Request request, SpanCustomizer customizer);
}
```

Since `brave.Span` implements `brave.SpanCustomizer`, it is just as easy for you
to pass to users.

Ex.
```java
for (MyTraceCallback callback : userCallbacks) {
  callback.request(request, span);
}
```

### RPC tracing
Check for [instrumentation written here](../instrumentation/) and [Zipkin's list](http://zipkin.io/pages/existing_instrumentations.html)
before rolling your own RPC instrumentation!

RPC tracing is often done automatically by interceptors. Under the scenes,
they add tags and events that relate to their role in an RPC operation.

Here's an example of a client span:
```java
// before you send a request, add metadata that describes the operation
span = tracer.newTrace().name("get").type(CLIENT);
span.tag("clnt/finagle.version", "6.36.0");
span.tag(TraceKeys.HTTP_PATH, "/api");
span.remoteEndpoint(Endpoint.builder()
    .serviceName("backend")
    .ipv4(127 << 24 | 1)
    .port(8080).build());

// when the request is scheduled, start the span
span.start();

// if you have callbacks for when data is on the wire, note those events
span.annotate(Constants.WIRE_SEND);
span.annotate(Constants.WIRE_RECV);

// when the response is complete, finish the span
span.finish();
```

#### One-Way tracing

Sometimes you need to model an asynchronous operation, where there is a
request, but no response. In normal RPC tracing, you use `span.finish()`
which indicates the response was received. In one-way tracing, you use
`span.flush()` instead, as you don't expect a response.

Here's how a client might model a one-way operation
```java
// start a new span representing a client request
oneWaySend = tracer.newSpan(parent).kind(Span.Kind.CLIENT);

// Add the trace context to the request, so it can be propagated in-band
tracing.propagation().injector(Request::addHeader)
                     .inject(oneWaySend.context(), request);

// fire off the request asynchronously, totally dropping any response
request.execute();

// start the client side and flush instead of finish
oneWaySend.start().flush();
```

And here's how a server might handle this..
```java
// pull the context out of the incoming request
extractor = tracing.propagation().extractor(Request::getHeader);

// convert that context to a span which you can name and add tags to
oneWayReceive = tracer.nextSpan(extractor, request)
    .name("process-request")
    .kind(SERVER)
    ... add tags etc.

// start the server side and flush instead of finish
oneWayReceive.start().flush();

// you should not modify this span anymore as it is complete. However,
// you can create children to represent follow-up work.
next = tracer.newSpan(oneWayReceive.context()).name("step2").start();
```

There's a working example of a one-way span [here](src/test/java/brave/features/async/OneWaySpanTest.java).

## Sampling

Sampling may be employed to reduce the data collected and reported out
of process. When a span isn't sampled, it adds no overhead (noop).

Sampling is an up-front decision, meaning that the decision to report
data is made at the first operation in a trace, and that decision is
propagated downstream.

By default, there's a global sampler that applies a single rate to all
traced operations. `Tracer.Builder.sampler` is how you indicate this,
and it defaults to trace every request.

### Declarative sampling

Some need to sample based on the type or annotations of a java method.

Most users will use a framework interceptor which automates this sort of
policy. Here's how they might work internally.

```java
// derives a sample rate from an annotation on a java method
DeclarativeSampler<Traced> sampler = DeclarativeSampler.create(Traced::sampleRate);

@Around("@annotation(traced)")
public Object traceThing(ProceedingJoinPoint pjp, Traced traced) throws Throwable {
  Span span = tracing.tracer().newTrace(sampler.sample(traced))...
  try {
    return pjp.proceed();
  } finally {
    span.finish();
  }
}
```

### Custom sampling

You may want to apply different policies depending on what the operation
is. For example, you might not want to trace requests to static resources
such as images, or you might want to trace all requests to a new api.

Most users will use a framework interceptor which automates this sort of
policy. Here's how they might work internally.

```java
Span newTrace(Request input) {
  SamplingFlags flags = SamplingFlags.NONE;
  if (input.url().startsWith("/experimental")) {
    flags = SamplingFlags.SAMPLED;
  } else if (input.url().startsWith("/static")) {
    flags = SamplingFlags.NOT_SAMPLED;
  }
  return tracer.newTrace(flags);
}
```

Note: the above is the basis for the built-in [http sampler](../instrumentation/http)

## Propagation
Propagation is needed to ensure activity originating from the same root
are collected together in the same trace. The most common propagation
approach is to copy a trace context from a client sending an RPC request
to a server receiving it.

For example, when an downstream Http call is made, its trace context is
sent along with it, encoded as request headers:

```
   Client Span                                                Server Span
┌──────────────────┐                                       ┌──────────────────┐
│                  │                                       │                  │
│   TraceContext   │           Http Request Headers        │   TraceContext   │
│ ┌──────────────┐ │          ┌───────────────────┐        │ ┌──────────────┐ │
│ │ TraceId      │ │          │ X─B3─TraceId      │        │ │ TraceId      │ │
│ │              │ │          │                   │        │ │              │ │
│ │ ParentSpanId │ │ Extract  │ X─B3─ParentSpanId │ Inject │ │ ParentSpanId │ │
│ │              ├─┼─────────>│                   ├────────┼>│              │ │
│ │ SpanId       │ │          │ X─B3─SpanId       │        │ │ SpanId       │ │
│ │              │ │          │                   │        │ │              │ │
│ │ Sampled      │ │          │ X─B3─Sampled      │        │ │ Sampled      │ │
│ └──────────────┘ │          └───────────────────┘        │ └──────────────┘ │
│                  │                                       │                  │
└──────────────────┘                                       └──────────────────┘
```

The names above are from [B3 Propagation](https://github.com/openzipkin/b3-propagation),
which is built-in to Brave and has implementations in many languages and
frameworks.

Most users will use a framework interceptor which automates propagation.
Here's how they might work internally.

Here's what client-side propagation might look like
```java
// configure a function that injects a trace context into a request
injector = tracing.propagation().injector(Request.Builder::addHeader);

// before a request is sent, add the current span's context to it
injector.inject(span.context(), request);
```

Here's what server-side propagation might look like
```java
// configure a function that extracts the trace context from a request
extractor = tracing.propagation().extractor(Request::getHeader);

// when a server receives a request, it joins or starts a new trace
span = tracer.nextSpan(extractor, request);
```

## Current Tracing Component
Brave supports a "current tracing component" concept which should only
be used when you have no other means to get a reference. This was made
for JDBC connections, as they often initialize prior to the tracing
component.

The most recent tracing component instantiated is available via
`Tracing.current()`. You there's also a shortcut to get only the tracer
via `Tracing.currentTracer()`. If you use either of these methods, do
noot cache the result. Instead, look them up each time you need them.

## Current Span

Brave supports a "current span" concept which represents the in-flight
operation. `Tracer.currentSpan()` can be used to add custom tags to a
span and `Tracer.nextSpan()` can be used to create a child of whatever
is in-flight.

### Setting a span in scope via custom executors

Many frameworks allow you to specify an executor which is used for user
callbacks. The type `CurrentTraceContext` implements all functionality
needed to support the current span. It also exposes utilities which you
can use to decorate executors.

```java
CurrentTraceContext currentTraceContext = new CurrentTraceContext.Default();
tracing = Tracing.newBuilder()
                 .currentTraceContext(currentTraceContext)
                 ...
                 .build();

Client c = Client.create();
c.setExecutorService(currentTraceContext.executorService(realExecutorService));
```

### Setting a span in scope manually

When writing new instrumentation, it is important to place a span you
created in scope as the current span. Not only does this allow users to
access it with `Tracer.currentSpan()`, but it also allows customizations
like SLF4J MDC to see the current trace IDs.

`Tracer.withSpanInScope(Span)` facilitates this and is most conveniently
employed via the try-with-resources idiom. Whenever external code might
be invoked (such as proceeding an interceptor or otherwise), place the
span in scope like this.

```java
try (SpanInScope ws = tracer.withSpanInScope(span)) {
  return inboundRequest.invoke();
} finally { // note the scope is independent of the span
  span.finish();
}
```

In edge cases, you may need to clear the current span temporarily. For
example, launching a task that should not be associated with the current
request. To do this, simply pass null to `withSpanInScope`.

```java
try (SpanInScope cleared = tracer.withSpanInScope(null)) {
  startBackgroundThread();
}
```

### Working with callbacks

Many libraries expose a callback model as opposed to an interceptor one.
When creating new instrumentation, you may find places where you need to
place a span in scope in one callback (like `onStart()`) and end the scope
in another callback (like `onFinish()`).

Provided the library guarantees these run on the same thread, you can
simply propagate the result of `Tracer.withSpanInScope(Span)` from the
starting callback to the closing one. This is typically done with a
request-scoped attribute.

Here's an example:
```java
class MyFilter extends Filter {
  public void onStart(Request request, Attributes attributes) {
    // Assume you have code to start the span and add relevant tags...

    // We now set the span in scope so that any code between here and
    // the end of the request can see it with Tracer.currentSpan()
    SpanInScope spanInScope = tracer.withSpanInScope(span);

    // We don't want to leak the scope, so we place it somewhere we can
    // lookup later
    attributes.put(SpanInScope.class, spanInScope);
  }

  public void onFinish(Response response, Attributes attributes) {
    // as long as we are on the same thread, we can read the span started above
    Span span = tracer.currentSpan();

    // Assume you have code to complete the span

    // We now remove the scope (which implicitly detaches it from the span)
    attributes.get(SpanInScope.class).close();
  }
}
```

### Working with callbacks that occur on different threads

The examples above work because the callbacks happen on the same thread.
You should not set a span in scope if you cannot close that scope on the
same thread. This may be the case in some asynchronous libraries. Often,
you will need to propagate the span directly in a custom attribute. This
will allow you to trace the RPC, even if this approach doesn't facilitate
use of `Tracer.currentSpan()` from external code.

Here's an example of explicit propagation:
```java
class MyFilter extends Filter {
  public void onStart(Request request, Attributes attributes) {
    // Assume you have code to start the span and add relevant tags...

    // We can't open a scope as onFinish happens on another thread.
    // Instead, we propagate the span manually so at least basic tracing
    // will work.
    attributes.put(Span.class, span);
  }

  public void onFinish(Response response, Attributes attributes) {
    // We can't rely on Tracer.currentSpan(), but we can rely on explicit
    // propagation
    Span span = attributes.get(Span.class);

    // Assume you have code to complete the span
  }
}
```

## Disabling Tracing

If you are in a situation where you need to turn off tracing at runtime,
invoke `Tracing.setNoop(true)`. This will turn any new spans into "noop"
spans, and drop any data until `Tracing.setNoop(false)` is invoked.

## Performance
Brave has been built with performance in mind. Using the core Span api,
you can record spans in sub-microseconds. When a span is sampled, there's
effectively no overhead (as it is a noop).

Unlike previous implementations, Brave 4 only needs one timestamp per
span. All annotations are recorded on an offset basis, using the less
expensive and more precise `System.nanoTime()` function.

## Upgrading from Brave 3
Brave 4 was designed to live alongside Brave 3. Using `TracerAdapter`,
you can navigate between apis, buying you time to update as appropriate.

Concepts are explained below, and there's an elaborate example of interop
[here](../brave-core/src/test/java/brave/interop/MixedBraveVersionsExample.java).

### Creating a Brave 3 instance
If your code uses Brave 3 apis, all you need to do is use `TracerAdapter`
to create a (Brave 3) .. Brave. You don't have to change anything else.

```java
Tracing brave4 = Tracing.newBuilder()...build();
Brave brave3 = TracerAdapter.newBrave(brave4.tracer());
```

### Converting between types
Those coding directly to both apis can use `TracerAdapter.toSpan` to
navigate between span types. This is useful when working with client and
local spans.

If you have a reference to a Brave 3 Span, you can convert like this:
```java
brave3Span = brave3.localSpanThreadBinder().getCurrentLocalSpan();
brave4Span = TracerAdapter.toSpan(brave4, brave3Span);
```

You can also attach Brave 4 Span to a Brave 3 thread binder like this:
```java
brave3Span = TracerAdapter.toSpan(brave4Span.context());
brave3.localSpanThreadBinder().setCurrentSpan(brave3Span);
```

#### Server spans
Brave 3's ServerTracer works slightly differently than Brave 3's client
or local tracers. Internally, it uses a `ServerSpan` which keeps track
of the original sample status. To interop with ServerTracer, use the
following hooks:

Use `TracerAdapter.getServerSpan` to get one of..
* a real span: when a sampled server request preceded this call
* a noop span: when a server request preceded this call, but was not sampled
* null: if no server request preceded this call

```java
brave4Span = TracerAdapter.getServerSpan(brave4, brave3.serverSpanThreadBinder());
// Since the current server span might be empty, you must check null
if (brave4Span != null) {
  // use or finish the span
}
```

If you have a reference to a Brave 4 span, and know it represents a server
request, use `TracerAdapter.setServerSpan` to attach it to Brave 3's thread
binder.
```java
TracerAdapter.setServerSpan(brave4Span.context(), brave3.serverSpanThreadBinder());
brave3.serverTracer().setServerSend(); // for example
```

### Dependencies
`io.zipkin.brave:brave`(Brave 4) is an optional dependency of
`io.zipkin.brave:brave-core`(Brave 3). To use `TracerAdapter`, you need
to declare an explicit dependency, and ensure both libraries are of the
same version.

### Notes
* Never mutate `com.twitter.zipkin.gen.Span` directly. Doing so will
  break the above integration.
* The above only works when `com.github.kristofa.brave.Brave` was
  instantiated via `TracerAdapter.newBrave`

## 128-bit trace IDs

Traditionally, Zipkin trace IDs were 64-bit. Starting with Zipkin 1.14,
128-bit trace identifiers are supported. This can be useful in sites that
have very large traffic volume, persist traces forever, or are re-using
externally generated 128-bit IDs.

If you want Brave to generate 128-bit trace identifiers when starting new
root spans, set `Tracer.Builder.traceId128Bit(true)`

When 128-bit trace ids are propagated, they will be twice as long as
before. For example, the `X-B3-TraceId` header will hold a 32-character
value like `163ac35c9f6413ad48485a3953bb6124`.

Before doing this, ensure your Zipkin setup is up-to-date, and downstream
instrumented services can read the longer 128-bit trace IDs.

Note: this only affects the trace ID, not span IDs. For example, span ids
within a trace are always 64-bit.

## Acknowledgements
Brave 4's design lends from past experience and similar open source work.
Quite a lot of decisions were driven by portability with Brave 3, and the
dozens of individuals involved in that. There are notable new aspects
that are borrowed or adapted from others.

### Stateful Span object
Brave 4 allows you to pass around a Span object which can report itself
to Zipkin when finished. This is better than using thread contexts in
some cases, particularly where many async hops are in use. The Span api
is derived from OpenTracing, narrowed to more cleanly match Zipkin's
abstraction. As a result, a bridge from Brave 4 to OpenTracing v0.20.2
is relatively little code. It should be able to implement future
versions of OpenTracing as well.

### Recorder architecture
Much of Brave 4's architecture is borrowed from Finagle, whose design
implies a separation between the propagated trace context and the data
collected in process. For example, Brave's MutableSpanMap is the same
overall design as Finagle's. The internals of MutableSpanMap were adapted
from [WeakConcurrentMap](https://github.com/raphw/weak-lock-free).

### Propagation Api
OpenTracing's Tracer type has methods to inject or extract a trace
context from a carrier. While naming is similar, Brave optimizes for
direct integration with carrier types (such as http request) vs routing
through an intermediate (such as a map). Brave also considers propagation
a separate api from the tracer.

### Current Tracer Api
The first design work of `Tracer.current()` started in [Brave 3](https://github.com/openzipkin/brave/pull/210),
which was itself influenced by Finagle's implicit Tracer api. This feature
is noted as edge-case, when other means to get a reference to a trace are
impossible. The only instrumentation that needed this was JDBC.

Returning a possibly null reference from `Tracer.current()` implies:
* Users never cache the reference returned (noted in javadocs)
* Less code and type constraints on Tracer vs a lazy forwarding delegate
* Less documentation as we don't have to explain what a Noop tracer does

### CurrentTraceContext Api
The first design of `CurrentTraceContext` was borrowed from `ContextUtils`
in Google's [instrumentation-java](https://github.com/google/instrumentation-java) project.
This was possible because of high collaboration between the projects and
an almost identical goal. Slight departures including naming (which prefers
Guice's naming conventions), and that the object scoped is a TraceContext
vs a Span.

Propagating a trace context instead of a span is a right fit for several reasons:
* `TraceContext` can be created without a reference to a tracer
* Common tasks like making child spans and staining log context only need the context
* Brave's recorder is keyed on context, so there's no feature loss in this choice

### Public namespace
Brave 4's public namespace is more defensive that the past, using a package
accessor design from [OkHttp](https://github.com/square/okhttp).
