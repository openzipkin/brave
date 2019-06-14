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
//   (the dependency is io.zipkin.reporter2:zipkin-sender-okhttp3)
sender = OkHttpSender.create("http://127.0.0.1:9411/api/v2/spans");
spanReporter = AsyncReporter.create(sender);

// Create a tracing component with the service name you want to see in Zipkin.
tracing = Tracing.newBuilder()
                 .localServiceName("my-service")
                 .spanReporter(spanReporter)
                 .build();

// Tracing exposes objects you might need, most importantly the tracer
tracer = tracing.tracer();

// Failing to close resources can result in dropped spans! When tracing is no
// longer needed, close the components you made in reverse order. This might be
// a shutdown hook for some users.
tracing.close();
spanReporter.close();
sender.close();
```

### Zipkin v1 setup
If you need to connect to an older version of the Zipkin api, you can use the following to use
Zipkin v1 format. See [zipkin-reporter](https://github.com/openzipkin/zipkin-reporter-java#legacy-encoding) for more.

```java
sender = URLConnectionSender.create("http://localhost:9411/api/v1/spans");
reporter = AsyncReporter.builder(sender)
                        .build(SpanBytesEncoder.JSON_V1);
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

### In-process Tracing

When tracing code that never leaves your process, run it inside a scoped span.
```java
// Start a new trace or a span within an existing trace representing an operation
ScopedSpan span = tracer.startScopedSpan("encode");
try {
  // The span is in "scope" meaning downstream code such as loggers can see trace IDs
  return encoder.encode();
} catch (RuntimeException | Error e) {
  span.error(e); // Unless you handle exceptions, you might not know the operation failed!
  throw e;
} finally {
  span.finish(); // always finish the span
}
```

When you need more features, or finer control, use the `Span` type:
```java
// Start a new trace or a span within an existing trace representing an operation
Span span = tracer.nextSpan().name("encode").start();
// Put the span in "scope" so that downstream code such as loggers can see trace IDs
try (SpanInScope ws = tracer.withSpanInScope(span)) {
  return encoder.encode();
} catch (RuntimeException | Error e) {
  span.error(e); // Unless you handle exceptions, you might not know the operation failed!
  throw e;
} finally {
  span.finish(); // note the scope is independent of the span. Always finish a span.
}
```

Both of the above examples report the exact same span on finish!

In the above example, the span will be either a new root span or the
next child in an existing trace. How this works is [described later](#current-span).

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

### Implicitly looking up the current span

Sometimes you won't know if a trace is in progress or not, and you don't
want users to do null checks. `brave.CurrentSpanCustomizer` adds to any
span that's in progress or drops data accordingly.

Ex.
```java
// Some DI configuration wires up the current span customizer
@Bean SpanCustomizer currentSpanCustomizer(Tracing tracing) {
  return CurrentSpanCustomizer.create(tracing);
}

// user code can then inject this without a chance of it being null.
@Inject SpanCustomizer span;

void userCode() {
  span.annotate("tx.started");
  ...
}
```

### RPC tracing
Check for [instrumentation written here](../instrumentation/) and [Zipkin's list](http://zipkin.io/pages/existing_instrumentations.html)
before rolling your own RPC instrumentation!

RPC tracing is often done automatically by interceptors. Under the scenes,
they add tags and events that relate to their role in an RPC operation.

Note: this is intentionally not an HTTP example, as we have [a special layer](../instrumentation/http)
for that.

Here's an example of a client span:
```java
// before you send a request, add metadata that describes the operation
span = tracer.nextSpan().name(service + "/" + method).kind(CLIENT);
span.tag("myrpc.version", "1.0.0");
span.remoteServiceName("backend");
span.remoteIpAndPort("172.3.4.1", 8108);

// Add the trace context to the request, so it can be propagated in-band
tracing.propagation().injector(Request::addHeader)
                     .inject(span.context(), request);

// when the request is scheduled, start the span
span.start();

// if there is an error, tag the span
span.tag("error", error.getCode());

// when the response is complete, finish the span
span.finish();
```

#### One-Way RPC tracing

Sometimes you need to model an asynchronous operation, where there is a
request, but no response. In normal RPC tracing, you use `span.finish()`
which indicates the response was received. In one-way tracing, you use
`span.flush()` instead, as you don't expect a response.

Here's how a client might model a one-way operation
```java
// start a new span representing a client request
oneWaySend = tracer.nextSpan().name(service + "/" + method).kind(CLIENT);
--snip--

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
oneWayReceive = nextSpan(tracer, extractor.extract(request))
    .name("process-request")
    .kind(SERVER)
    ... add tags etc.

// start the server side and flush instead of finish
oneWayReceive.start().flush();

// you should not modify this span anymore as it is complete. However,
// you can create children to represent follow-up work.
next = tracer.newSpan(oneWayReceive.context()).name("step2").start();
```

**Note** The above propagation logic is a simplified version of our [http handlers](https://github.com/openzipkin/brave/tree/master/instrumentation/http#http-server).

There's a working example of a one-way span [here](src/test/java/brave/features/async/OneWaySpanTest.java).

## Sampling

Sampling may be employed to reduce the data collected and reported out
of process. When a span isn't sampled, it adds no overhead (noop).

Sampling is an up-front decision, meaning that the decision to report
data is made at the first operation in a trace, and that decision is
propagated downstream.

By default, there's a global sampler that applies a single rate to all
traced operations. `Tracing.Builder.sampler` is how you indicate this,
and it defaults to trace every request.

For example, to choose 10 traces every second, you'd initialize like so:
```java
tracing = Tracing.newBuilder()
                 .sampler(RateLimitingSampler.create(10))
--snip--
                 .build();
```

### Declarative sampling

Some need to sample based on the type or annotations of a java method.

Most users will use a framework interceptor which automates this sort of
policy. Here's how they might work internally.

```java
// derives a sample rate from an annotation on a java method
DeclarativeSampler<Traced> sampler = DeclarativeSampler.create(Traced::sampleRate);

@Around("@annotation(traced)")
public Object traceThing(ProceedingJoinPoint pjp, Traced traced) throws Throwable {
  // When there is no trace in progress, this decides using an annotation
  Sampler decideUsingAnnotation = declarativeSampler.toSampler(traced);
  Tracer tracer = tracing.tracer().withSampler(decideUsingAnnotation);

  // This code looks the same as if there was no declarative override
  ScopedSpan span = tracer.startScopedSpan(spanName(pjp));
  try {
    return pjp.proceed();
  } catch (RuntimeException | Error e) {
    span.error(e);
    throw e;
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
Sampler fallback = tracing.sampler();

Span nextSpan(final Request input) {
  Sampler requestBased = Sampler() {
    @Override public boolean isSampled(long traceId) {
      if (input.url().startsWith("/experimental")) {
        return true;
      } else if (input.url().startsWith("/static")) {
        return false;
      }
      return fallback.isSampled(traceId);
    }
  };
  return tracer.withSampler(requestBased).nextSpan();
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
span = tracer.nextSpan(extractor.extract(request));
```

### Propagating extra fields

Sometimes you need to propagate extra fields, such as a request ID or an alternate trace context.
For example, if you are in a Cloud Foundry environment, you might want to pass the request ID:

```java
// when you initialize the builder, define the extra field you want to propagate
tracingBuilder.propagationFactory(
  ExtraFieldPropagation.newFactory(B3Propagation.FACTORY, "x-vcap-request-id")
);

// later, you can tag that request ID or use it in log correlation
requestId = ExtraFieldPropagation.get("x-vcap-request-id");
```

You may also need to propagate a trace context you aren't using. For example, you may be in an
Amazon Web Services environment, but not reporting data to X-Ray. To ensure X-Ray can co-exist
correctly, pass-through its tracing header like so.

```java
tracingBuilder.propagationFactory(
  ExtraFieldPropagation.newFactory(B3Propagation.FACTORY, "x-amzn-trace-id")
);
```

#### Prefixed fields
You can also prefix fields, if they follow a common pattern. For example, the following will
propagate the field "x-vcap-request-id" as-is, but send the fields "country-code" and "user-id"
on the wire as "baggage-country-code" and "baggage-user-id" respectively.

Setup your tracing instance with allowed fields:
```java
tracingBuilder.propagationFactory(
  ExtraFieldPropagation.newFactoryBuilder(B3Propagation.FACTORY)
                       .addField("x-vcap-request-id")
                       .addPrefixedFields("baggage-", Arrays.asList("country-code", "user-id"))
                       .build()
);
```

Later, you can call below to affect the country code of the current trace context
```java
ExtraFieldPropagation.set("country-code", "FO");
String countryCode = ExtraFieldPropagation.get("country-code");
```

Or, if you have a reference to a trace context, use it explicitly
```java
ExtraFieldPropagation.set(span.context(), "country-code", "FO");
String countryCode = ExtraFieldPropagation.get(span.context(), "country-code");
```

### Extracting a propagated context
The `TraceContext.Extractor<C>` reads trace identifiers and sampling status
from an incoming request or message. The carrier is usually a request object
or headers.

This utility is used in standard instrumentation like [HttpServerHandler](../instrumentation/http/src/main/java/brave/http/HttpServerHandler.java),
but can also be used for custom RPC or messaging code.

`TraceContextOrSamplingFlags` is usually only used with `Tracer.nextSpan(extracted)`, unless you are
sharing span IDs between a client and a server.

### Sharing span IDs between client and server

A normal instrumentation pattern is creating a span representing the server
side of an RPC. `Extractor.extract` might return a complete trace context when
applied to an incoming client request. `Tracer.joinSpan` attempts to continue
the this trace, using the same span ID if supported, or creating a child span
if not. When span ID is shared, data reported includes a flag saying so.

Here's an example of B3 propagation:

```
                              ┌───────────────────┐      ┌───────────────────┐
 Incoming Headers             │   TraceContext    │      │   TraceContext    │
┌───────────────────┐(extract)│ ┌───────────────┐ │(join)│ ┌───────────────┐ │
│ X─B3-TraceId      │─────────┼─┼> TraceId      │ │──────┼─┼> TraceId      │ │
│                   │         │ │               │ │      │ │               │ │
│ X─B3-ParentSpanId │─────────┼─┼> ParentSpanId │ │──────┼─┼> ParentSpanId │ │
│                   │         │ │               │ │      │ │               │ │
│ X─B3-SpanId       │─────────┼─┼> SpanId       │ │──────┼─┼> SpanId       │ │
└───────────────────┘         │ │               │ │      │ │               │ │
                              │ │               │ │      │ │  Shared: true │ │
                              │ └───────────────┘ │      │ └───────────────┘ │
                              └───────────────────┘      └───────────────────┘
```

Some propagation systems only forward the parent span ID, detected when
`Propagation.Factory.supportsJoin() == false`. In this case, a new span ID is
always provisioned and the incoming context determines the parent ID.

Here's an example of AWS propagation:
```
                              ┌───────────────────┐      ┌───────────────────┐
 x-amzn-trace-id              │   TraceContext    │      │   TraceContext    │
┌───────────────────┐(extract)│ ┌───────────────┐ │(join)│ ┌───────────────┐ │
│ Root              │─────────┼─┼> TraceId      │ │──────┼─┼> TraceId      │ │
│                   │         │ │               │ │      │ │               │ │
│ Parent            │─────────┼─┼> SpanId       │ │──────┼─┼> ParentSpanId │ │
└───────────────────┘         │ └───────────────┘ │      │ │               │ │
                              └───────────────────┘      │ │  SpanId: New  │ │
                                                         │ └───────────────┘ │
                                                         └───────────────────┘
```

Note: Some span reporters do not support sharing span IDs. For example, if you
set `Tracing.Builder.spanReporter(amazonXrayOrGoogleStackdrive)`, disable join
via `Tracing.Builder.supportsJoin(false)`. This will force a new child span on
`Tracer.joinSpan()`.

### Implementing Propagation

`TraceContext.Extractor<C>` is implemented by a `Propagation.Factory` plugin. Internally, this code
will create the union type `TraceContextOrSamplingFlags` with one of the following:
* `TraceContext` if trace and span IDs were present.
* `TraceIdContext` if a trace ID was present, but not span IDs.
* `SamplingFlags` if no identifiers were present

Some `Propagation` implementations carry extra data from point of extraction (ex reading incoming
headers) to injection (ex writing outgoing headers). For example, it might carry a request ID. When
implementations have extra data, here's how they handle it.
* If a `TraceContext` was extracted, add the extra data as `TraceContext.extra()`
* Otherwise, add it as `TraceContextOrSamplingFlags.extra()`, which `Tracer.nextSpan` handles.

## Handling Finished Spans
By default, data recorded before (`Span.finish()`) are reported to Zipkin
via what's passed to `Tracing.Builder.spanReporter`. `FinishedSpanHandler`
can modify or drop data before it goes to Zipkin. It can even intercept
data that is not sampled for Zipkin.

`FinishedSpanHandler` can return false to drop spans that you never want
to see in Zipkin. This should be used carefully as the spans dropped
should not have children.

Here's an example of SQL COMMENT spans so they don't clutter Zipkin.
```java
tracingBuilder.addFinishedSpanHandler(new FinishedSpanHandler() {
  @Override public boolean handle(TraceContext context, MutableSpan span) {
    return !"comment".equals(span.name());
  }
});
```

Another example is redaction: you may need to scrub tags to ensure no
personal information like social security numbers end up in Zipkin.
```java
tracingBuilder.addFinishedSpanHandler(new FinishedSpanHandler() {
  @Override public boolean handle(TraceContext context, MutableSpan span) {
    span.forEachTag((key, value) ->
      value.replaceAll("[0-9]{3}\\-[0-9]{2}\\-[0-9]{4}", "xxx-xx-xxxx")
    );
    return true; // retain the span
  }
});
```
An example of redaction is [here](src/test/java/brave/features/handler/RedactingFinishedSpanHandlerTest.java)

### Sampling locally
While Brave defaults to report 100% of data to Zipkin, many will use a
lower percentage like 1%. This is called sampling and the decision is
maintained throughout the trace, across requests consistently. Sampling
has disadvantages. For example, statistics on sampled data is usually
misleading by nature of not observing all durations.

`FinishedSpanHandler` returns `alwaysSampleLocal()` to indicate whether
it should see all data, or just all data sent to Zipkin. You can override
this to true to observe all operations.

Here's an example of metrics handling:
```java
tracingBuilder.addFinishedSpanHandler(new FinishedSpanHandler() {
  @Override public boolean alwaysSampleLocal() {
    return true; // since we want to always see timestamps, we have to always record
  }

  @Override public boolean handle(TraceContext context, MutableSpan span) {
    if (namesToAlwaysTime.contains(span.name())) {
      registry.timer("spans", "name", span.name())
          .record(span.finishTimestamp() - span.startTimestamp(), MICROSECONDS);
    }
    return true; // retain the span
  }
});
```
An example of metrics handling is [here](src/test/java/brave/features/handler/MetricsFinishedSpanHandler.java)

## Current Tracing Component
Brave supports a "current tracing component" concept which should only
be used when you have no other means to get a reference. This was made
for JDBC connections, as they often initialize prior to the tracing
component.

The most recent tracing component instantiated is available via
`Tracing.current()`. There's also a shortcut to get only the tracer
via `Tracing.currentTracer()`. If you use either of these methods, do
not cache the result. Instead, look them up each time you need them.

## Current Span

Brave supports a "current span" concept which represents the in-flight
operation.

`Tracer.currentSpanCustomizer()` never returns null and `SpanCustomizer`
is generally a safe object to expose to third-party code to add tags.

`Tracer.currentSpan()` should be reserved for framework code that cannot
reference the span it wants to finish, flush or abandon explicitly.

`Tracer.nextSpan()` uses the "current span" to determine a parent. This
creates a child of whatever is in-flight.

### Setting a span in scope via custom executors

Many frameworks allow you to specify an executor which is used for user
callbacks. The type `CurrentTraceContext` implements all functionality
needed to support the current span. It also exposes utilities which you
can use to decorate executors.

```java
CurrentTraceContext currentTraceContext = ThreadLocalCurrentTraceContext.create();
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
access it with `Tracer.currentSpanCustomizer()`, but it also allows
customizations like SLF4J MDC to see the current trace IDs.

The easiest way to scope a span is via `Tracer.startScopedSpan(name)`:
```java
ScopedSpan span = tracer.startScopedSpan("encode");
try {
  // The span is in "scope" meaning downstream code such as loggers can see trace IDs
  return encoder.encode();
} catch (RuntimeException | Error e) {
  span.error(e); // Unless you handle exceptions, you might not know the operation failed!
  throw e;
} finally {
  span.finish(); // always finish the span
}
```

If doing RPC or otherwise advanced tracing, `Tracer.withSpanInScope(Span)`
scopes an existing span via the try-with-resources idiom. Whenever
external code might be invoked (such as proceeding an interceptor or
otherwise), place the span in scope like this.

```java
try (SpanInScope ws = tracer.withSpanInScope(span)) {
  return inboundRequest.invoke();
} catch (RuntimeException | Error e) {
  span.error(e);
  throw e;
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
    // the end of the request can modify it with Tracer.currentSpan()
    // or Tracer.currentSpanCustomizer()
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
    attributes.remove(SpanInScope.class).close();
  }
}
```

Sometimes you have to instrument a library where There's no attribute
namespace shared across request and response. For this scenario, you can
use `ThreadLocalSpan` to temporarily store the span between callbacks.

Here's an example:
```java
class MyFilter extends Filter {
  final ThreadLocalSpan threadLocalSpan;

  public void onStart(Request request) {
    // Assume you have code to start the span and add relevant tags...

    // We now set the span in scope so that any code between here and
    // the end of the request can see it with Tracer.currentSpan()
    threadLocalSpan.set(span);
  }

  public void onFinish(Response response, Attributes attributes) {
    // as long as we are on the same thread, we can read the span started above
    Span span = threadLocalSpan.remove();
    if (span == null) return;

    // Assume you have code to complete the span
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
    Span span = attributes.remove(Span.class);

    // Assume you have code to complete the span
  }
}
```

### Customizing in-process propagation (Ex. log correlation)

The `CurrentTraceContext` makes a trace context visible, such that spans
aren't accidentally added to the wrong trace. It also accepts hooks like
log correlation.

For example, if you use Log4J 2, you can copy trace IDs to your logging
context with [our decorator](../context/log4j2/README.md):
```java
tracing = Tracing.newBuilder()
    .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
       .addScopeDecorator(ThreadContextScopeDecorator.create())
       .build()
    )
    ...
    .build();
```

Besides logging, other tools are available. [StrictScopeDecorator](src/main/java/brave/propagation/StrictScopeDecorator.java) can
help find out when code is not closing scopes properly. This can be
useful when writing or diagnosing custom instrumentation.

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

## Troubleshooting instrumentation
Instrumentation problems can lead to scope leaks and orphaned data. When
testing instrumentation, use [StrictScopeDecorator](src/main/java/brave/propagation/StrictScopeDecorator.java), as it will throw
errors on known scoping problems.

If you see data with the annotation `brave.flush`, you may have an
instrumentation bug. To see more information, set the Java logger named
`brave.internal.recorder.PendingSpans` to FINE level. Do not do this in
production as tracking abandoned data incurs higher overhead.

Note: When using log4j2, set the following to ensure log settings apply:
`-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager`

## Unit testing instrumentation

When writing unit tests, there are a few tricks that will make bugs
easier to find:

* Report spans into a concurrent queue, so you can read them in tests
* Use `StrictScopeDecorator` to reveal subtle thread-related propagation bugs
* Unconditionally cleanup `Tracing.current()`, to prevent leaks

Here's an example setup for your unit test fixture:
```java
ConcurrentLinkedDeque<Span> spans = new ConcurrentLinkedDeque<>();

Tracing tracing = Tracing.newBuilder()
                 .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
                   .addScopeDecorator(StrictScopeDecorator.create())
                   .build()
                 )
                 .spanReporter(spans::add)
                 .build();

  @After public void close() {
    Tracing current = Tracing.current();
    if (current != null) current.close();
  }
```

## Upgrading from Brave 3
Brave 4 was designed to live alongside Brave 3. Using `TracerAdapter`,
you can navigate between apis, buying you time to update as appropriate.

Concepts are explained below, and there's an elaborate example of interop
[here](https://github.com/openzipkin/brave/blob/4.13.4/archive/brave-core/src/test/java/brave/interop/MixedBraveVersionsExample.java).

Note: The last version of Brave 3 types is `io.zipkin.brave:brave-core:4.13.4`
`TracerAdapter` also should work with later versions of `io.zipkin.brave:brave`

### Creating a Brave 3 instance
If your code uses Brave 3 apis, all you need to do is use `TracerAdapter`
to create a (Brave 3) .. Brave. You don't have to change anything else.

```java
Tracing brave4 = Tracing.newBuilder()...build();
Brave brave3 = TracerAdapter.newBrave(brave4.tracer());
```

### Converting between types
Those coding directly to both apis can use `TracerAdapter.toSpan` to
navigate between span types. This is useful when working with client RPC
and in-process (local) spans.

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
root spans, set `Tracing.Builder.traceId128Bit(true)`

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

### ScopedSpan type
Brave 4.19 introduced `ScopedSpan` which derives influence primarily from
[OpenCensus SpanBuilder.startScopedSpan() Api](https://github.com/census-instrumentation/opencensus-java/blob/f852100ad9b77522fabd3c0b29e78202aed3a26e/api/src/main/java/io/opencensus/trace/SpanBuilder.java#L229), a convenience type to
start work and ensure scope is visible downstream. One main difference here is
we assume the scoped span is used in the same thread, so mark it as such. Also,
we reduce the size of the api to help secure users against code drift.

### Error Handling
Brave 4.19 added `ErrorParser`, internally used by `Span.error(Throwable)`. This
design incubated in [Spring Cloud Sleuth](https://github.com/spring-cloud/spring-cloud-sleuth) prior to becoming a primary type here.
Our care and docs around error handling in general is credit to [Nike Wingtips](https://github.com/Nike-Inc/wingtips#warning-about-error-handling-when-using-try-with-resources-to-autoclose-spans).

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
The first design work of `Tracing.currentTracer()` started in [Brave 3](https://github.com/openzipkin/brave/pull/210),
which was itself influenced by Finagle's implicit Tracer api. This feature
is noted as edge-case, when other means to get a reference to a trace are
impossible. The only instrumentation that needed this was JDBC.

Returning a possibly null reference from `Tracing.currentTracer()` implies:
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

### Local Sampling flag
The [Census](https://opencensus.io/) project has a concept of a[SampledSpanStore](https://github.com/census-instrumentation/opencensus-java/blob/master/api/src/main/java/io/opencensus/trace/export/SampledSpanStore.java).
Typically, you configure a span name pattern or choose [individual spans](https://github.com/census-instrumentation/opencensus-java/blob/660e8f375bb483a3eb817940b3aa8534f86da314/api/src/main/java/io/opencensus/trace/EndSpanOptions.java#L65)
for local (in-process) storage. This storage is used to power
administrative pages named zPages. For example, [Tracez](https://github.com/census-instrumentation/opencensus-java/blob/660e8f375bb483a3eb817940b3aa8534f86da314/contrib/zpages/src/main/java/io/opencensus/contrib/zpages/TracezZPageHandler.java#L220)
displays spans sampled locally which have errors or crossed a latency
threshold.

The "sample local" vocab in Census was re-used in Brave to describe the
act of keeping data that isn't going to necessarily end up in Zipkin.

The main difference in Brave is implementation. For example, the `sampledLocal`
flag in Brave is a part of the TraceContext and so can be triggered when
headers are parsed. This is needed to provide custom sampling mechanisms.
Also, Brave has a small core library and doesn't package any specific
storage abstraction, admin pages or latency processors. As such, we can't
define specifically what sampled local means as Census' could. All it
means is that `FinishedSpanHandler` will see data for that trace context.

### FinishedSpanHandler Api
Brave had for a long time re-used zipkin's Reporter library, which is
like Census' SpanExporter in so far that they both allow pushing a specific
format elsewhere, usually to a service. Brave's [FinishedSpanHandler](https://github.com/openzipkin/brave/blob/master/brave/src/main/java/brave/handler/FinishedSpanHandler.java)
is a little more like Census' [SpanExporter.Handler](https://github.com/census-instrumentation/opencensus-java/blob/master/api/src/main/java/io/opencensus/trace/export/SpanExporter.java)
in so far as the structure includes the trace context.. something we need
access to in order to do things like advanced sampling.

Where it differs is that the `MutableSpan` in Brave is.. well.. mutable,
and this allows you to cheaply change data. Also, it isn't a struct based
on a proto, so it can hold references to objects like `Exception`. This
allows us to render data into different formats such as [Amazon's stack frames](https://docs.aws.amazon.com/xray/latest/devguide/xray-api-segmentdocuments.html)
without having to guess what will be needed by parsing up front. As
error parsing is deferred, overhead is less in cases where errors are not
recorded (such as is the case on spans intentionally dropped).

### Public namespace
Brave 4's public namespace is more defensive that the past, using a package
accessor design from [OkHttp](https://github.com/square/okhttp).

### Rate-limiting sampler
`RateLimitingSampler` was made to allow Amazon X-Ray rules to be
expressed in Brave. We considered their [Reservoir design](https://github.com/aws/aws-xray-sdk-java/blob/2.0.1/aws-xray-recorder-sdk-core/src/main/java/com/amazonaws/xray/strategy/sampling/reservoir/Reservoir.java).
Our implementation differs as it removes a race condition and attempts
to be more fair by distributing accept decisions every decisecond.
