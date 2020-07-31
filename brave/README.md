Brave Core Library
==============

Brave is a library used to capture and report latency information about
distributed operations to Zipkin. Most users won't use Brave directly,
rather libraries or frameworks than employ Brave on their behalf.

This module includes tracer creates and joins spans that model the
latency of potentially distributed work. It also includes libraries to
propagate the trace context over network boundaries, for example, via
http headers.

## Setup

Most importantly, you need a Tracer, usually configured to [report to Zipkin](https://github.com/openzipkin/zipkin-reporter-java).

Here's an example setup that sends trace data (spans) to Zipkin over
HTTP (as opposed to Kafka).

```java
// Configure a reporter, which controls how often spans are sent
//   (this dependency is io.zipkin.reporter2:zipkin-sender-okhttp3)
sender = OkHttpSender.create("http://127.0.0.1:9411/api/v2/spans");
//   (this dependency is io.zipkin.reporter2:zipkin-reporter-brave)
zipkinSpanHandler = AsyncZipkinSpanHandler.create(sender);

// Create a tracing component with the service name you want to see in Zipkin.
tracing = Tracing.newBuilder()
                 .localServiceName("my-service")
                 .addSpanHandler(zipkinSpanHandler)
                 .build();

// Tracing exposes objects you might need, most importantly the tracer
tracer = tracing.tracer();

// Failing to close resources can result in dropped spans! When tracing is no
// longer needed, close the components you made in reverse order. This might be
// a shutdown hook for some users.
tracing.close();
zipkinSpanHandler.close();
sender.close();
```

### Zipkin v1 setup
If you need to connect to an older version of the Zipkin api, you can use the following to use
Zipkin v1 format. See [zipkin-reporter](https://github.com/openzipkin/zipkin-reporter-java#legacy-encoding) for more.

```java
sender = URLConnectionSender.create("http://localhost:9411/api/v1/spans");
reporter = AsyncReporter.builder(sender)
                        .build(SpanBytesEncoder.JSON_V1); // don't forget to close!
zipkinSpanHandler = ZipkinSpanHandler.create(reporter)
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

The `Tag` type is a helper that works with all variants of `Span`. For
common or expensive tags, consider implementing `Tag` or re-using a
built-in one.

Here's an example of a potentially expensive tag:
```java
SUMMARY_TAG = new Tag<Summarizer>("summary") {
  @Override protected String parseValue(Summarizer input, TraceContext context) {
    return input.computeSummary();
  }
}

// This works for any variant of span
SUMMARY_TAG.tag(summarizer, span);
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

### Remote spans
Check for [instrumentation and abstractions](../instrumentation/) and [Zipkin's list](https://zipkin.io/pages/existing_instrumentations.html)
for common patterns like client-server or messaging communication. Please at
least read [this doc](../instrumentation/README.md) before deciding to write
your own code to represent remote communication.

If you really think that the existing abstractions do not match your need, and
you want to model your request yourself, you generally need to...
1. Start the span and add trace headers to the request
2. Put the span in scope so things like log integration works
3. Invoke the request
4. Catch any errors
5. Complete the span

Here's a simplified example:
```java
requestWrapper = new ClientRequestWrapper(request);
span = tracer.nextSpan(sampler, requestWrapper); // 1.
tracing.propagation().injector(ClientRequestWrapper::addHeader)
                     .inject(span.context(), requestWrapper);
span.kind(request.spanKind());
span.name("Report");
span.start();
try (Scope ws = currentTraceContext.newScope(span.context())) { // 2.
  return invoke(request); // 3.
} catch (Throwable e) {
  span.error(error); // 4.
  throw e;
} finally {
  span.finish(); // 5.
}
```

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
SamplerFunction<Traced> samplerFunction = DeclarativeSampler.createWithRate(Traced::sampleRate);

@Around("@annotation(traced)")
public Object traceThing(ProceedingJoinPoint pjp, Traced traced) throws Throwable {
  // When there is no trace in progress, this overrides the decision based on the annotation
  ScopedSpan span = tracer.startScopedSpan(spanName(pjp), samplerFunction, traced);
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
policy.

Here's a simplified version of how this might work internally.
```java
SamplerFunction<Request> requestBased = (request) -> {
  if (request.url().startsWith("/experimental")) {
    return true;
  } else if (request.url().startsWith("/static")) {
    return false;
  }
  return null;
};

Span nextSpan(final Request input) {
  return tracer.nextSpan(requestBased, input);
}
```

Note: the above is the basis for the built-in [http sampler](../instrumentation/http)


## Baggage
Sometimes you need to propagate additional fields, such as a request ID or an alternate trace
context.

For example, if you have a need to know a specific request's country code, you can
propagate it through the trace as an HTTP header with the same name:
```java
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;

// Configure your baggage field
COUNTRY_CODE = BaggageField.create("country-code");

// When you initialize the builder, add the baggage you want to propagate
tracingBuilder.propagationFactory(
  BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
                    .add(SingleBaggageField.remote(COUNTRY_CODE))
                    .build()
);

// Later, you can retrieve that country code in any of the services handling the trace
// and add it as a span tag or do any other processing you want with it.
countryCode = COUNTRY_CODE.getValue(context);
```

### Using `BaggageField`
As long as a field is configured with `BaggagePropagation`, local reads and
updates are possible in-process.

If added to the `BaggagePropagation.Builder`, you can call below to affect
the country code of the current trace context:
```java
COUNTRY_CODE.updateValue("FO");
String countryCode = COUNTRY_CODE.getValue();
```

Or, if you have a reference to a trace context, it is more efficient to use it explicitly:
```java
COUNTRY_CODE.updateValue(span.context(), "FO");
String countryCode = COUNTRY_CODE.get(span.context());
Tags.BAGGAGE_FIELD.tag(COUNTRY_CODE, span);
```

### Remote Baggage

By default, the name used as a propagation key (header) by `addRemoteField()` is the same as
the lowercase variant of the field name. You can override this by supplying different key
names. Note: they will be lower-cased.

For example, the following will propagate the field "x-vcap-request-id" as-is, but send the
fields "countryCode" and "userId" on the wire as "baggage-country-code" and "baggage-user-id"
respectively.
```java
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;

REQUEST_ID = BaggageField.create("x-vcap-request-id");
COUNTRY_CODE = BaggageField.create("countryCode");
USER_ID = BaggageField.create("userId");

tracingBuilder.propagationFactory(
    BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
                      .add(SingleBaggageField.remote(REQUEST_ID))
                      .add(SingleBaggageField.newBuilder(COUNTRY_CODE)
                                             .addKeyName("baggage-country-code").build())
                      .add(SingleBaggageField.newBuilder(USER_ID)
                                             .addKeyName("baggage-user-id").build())
                      .build()
);
```
### Correlation

You can also integrate baggage with other correlated contexts such as logging:
```java
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import brave.baggage.CorrelationScopeConfig.SingleCorrelationField;

AMZN_TRACE_ID = BaggageField.create("x-amzn-trace-id");

// Allow logging patterns like %X{traceId} %X{x-amzn-trace-id}
decorator = MDCScopeDecorator.newBuilder()
                             .add(SingleCorrelationField.create(AMZN_TRACE_ID)).build()

tracingBuilder.propagationFactory(BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
                                                    .add(SingleBaggageField.remote(AMZN_TRACE_ID))
                                                    .build())
              .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
                                                                 .addScopeDecorator(decorator)
                                                                 .build())
```

#### Field mapping
Your log correlation properties may not be the same as the baggage field names. You can
override them in the builder as needed.

Ex. If your log property is %X{trace-id}, you can do this:
```java
import brave.baggage.CorrelationScopeConfig.SingleCorrelationField;

scopeBuilder.clear() // TRACE_ID is a default field!
            .add(SingleCorrelationField.newBuilder(BaggageFields.TRACE_ID)
                                       .name("trace-id").build())
```

### Appropriate usage

Brave is an infrastructure library: you will create lock-in if you expose its apis into
business code. Prefer exposing your own types for utility functions that use this class as this
will insulate you from lock-in.

While it may seem convenient, do not use this for security context propagation as it was not
designed for this use case. For example, anything placed in here can be accessed by any code in
the same classloader!

### Passing through alternate trace contexts

You may also need to propagate an second trace context transparently. For example, when in an
Amazon Web Services environment, but not reporting data to X-Ray. To ensure X-Ray can co-exist
correctly, pass-through its tracing header like so.

```java
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;

OTHER_TRACE_ID = BaggageField.create("x-amzn-trace-id");

tracingBuilder.propagationFactory(
  BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
                    .add(SingleBaggageField.remote(OTHER_TRACE_ID))
                    .build()
);
```

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

### Extracting a propagated context
The `TraceContext.Extractor<R>` reads trace identifiers and sampling status
from an incoming request or message. The request is usually a request object
or headers.

This utility is used in standard instrumentation like [HttpServerHandler](../instrumentation/http/src/main/java/brave/http/HttpServerHandler.java),
but can also be used for custom RPC or messaging code.

`TraceContextOrSamplingFlags` is usually only used with `Tracer.nextSpan(extracted)`, unless you are
sharing span IDs between a client and a server.

### Sharing span IDs between client and server

A normal instrumentation pattern is creating a span representing the server
side of an RPC. `Extractor.extract` might return a complete trace context when
applied to an incoming client request. `Tracer.joinSpan` attempts to continue
this trace, using the same span ID if supported, or creating a child span
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

`TraceContext.Extractor<R>` is implemented by a `Propagation.Factory` plugin. Internally, this code
will create the union type `TraceContextOrSamplingFlags` with one of the following:
* `TraceContext` if trace and span IDs were present.
* `TraceIdContext` if a trace ID was present, but not span IDs.
* `SamplingFlags` if no identifiers were present

Some `Propagation` implementations carry extra state from point of extraction (ex reading incoming
headers) to injection (ex writing outgoing headers). For example, it might carry a request ID. When
implementations have extra state, here's how they handle it.
* If a `TraceContext` was extracted, add the extra state as `TraceContext.extra()`
* Otherwise, add it as `TraceContextOrSamplingFlags.extra()`, which `Tracer.nextSpan` handles.

## Handling Spans
By default, data recorded before (`Span.finish()`) are reported to Zipkin
to a `SpanHandler`. For example, `Tracing.Builder.spanReporter` is an instance
of a `SpanHandler` that reports data to Zipkin when sampled.

You can create you own `SpanHandler`s to modify or drop data before it goes to
Zipkin (or anywhere else). It can even intercept data that is not sampled!

Here's an example of SQL COMMENT spans so they don't clutter Zipkin.
```java
tracingBuilder.addSpanHandler(new SpanHandler() {
  @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
    return !"comment".equals(span.name());
  }
});
```

Another example is redaction: you may need to scrub tags to ensure no
personal information like credit card numbers end up in Zipkin.
```java
tracingBuilder.addSpanHandler(new SpanHandler() {
  @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
    span.forEachTag((key, value) ->
      value.replaceAll("[0-9]{4}-[0-9]{4}-[0-9]{4}-[0-9]{4}", "xxxx-xxxx-xxxx-xxxx")
    );
    return true; // retain the span
  }
});
```
An example of redaction is [here](src/test/java/brave/features/handler/RedactingSpanHandlerTest.java)

### Sampling locally
While Brave defaults to report 100% of data to Zipkin, many will use a
lower percentage like 1%. This is called sampling and the decision is
maintained throughout the trace, across requests consistently. Sampling
has disadvantages. For example, statistics on sampled data is usually
misleading by nature of not observing all durations.

Call `Tracing.Builder.alwaysSampleLocal()` to indicate if `SpanHandler`s
should see all data or only when data sampled remotely (`TraceContext.sampled()`).

This easiest way to configure multiple aspects is with a `TracingCustomizer`.
Here's an example that generates duration metrics, regardless of remote sampling:
```java
// Metrics wants duration of all operations, not just ones sampled remotely
customizer = b -> b.alwaysSampleLocal().addSpanHandler(new SpanHandler() {
  @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
    if (namesToAlwaysTime.contains(span.name())) {
      registry.timer("spans", "name", span.name())
              .record(span.finishTimestamp() - span.startTimestamp(), MICROSECONDS);
    }
    return true; // retain the span
  }
});

// Later, your configuration library invokes the customize method
customizer.customize(tracingBuilder);
```
A span metrics example is [here](src/test/java/brave/features/handler/SpanMetricsCustomizer.java)

### Non-Zipkin Span Reporting example
When reporting to a Zipkin compatible collector, use [io.zipkin.reporter2:zipkin-reporter-brave](https://github.com/openzipkin/zipkin-reporter-java).

The below example highlights notes for those sending data into a different
format. Notably, most trace systems only need data at the `end` hook.

```java
public boolean end(TraceContext context, MutableSpan span, Cause cause) {
  // Sampled means "remote sampled", e.g. to the tracing system.
  if (!Boolean.TRUE.equals(context.sampled())) return true;

  // span.tags("error") is only set when instrumentation sets it. If your
  // format requires span.tags("error"), use Tags.ERROR when span.error()
  // is set, but span.tags("error") is not. Some formats will look at the
  // stack trace instead.
  maybeAddErrorTag(context, span);
  MyFormat customFormat = convert(context, span);
  nonZipkinReporter.report(converted);
  return true;
}
```

### Child Counting Example
Some data formats desire knowing how many spans a parent created. Below is an
example of how to do that, using [WeakConcurrentMap](https://github.com/raphw/weak-lock-free).

```java
static final class TagChildCountDirect extends SpanHandler {
--snip--
  public boolean begin(TraceContext context, MutableSpan span, @Nullable TraceContext parent) {
    if (!context.isLocalRoot()) { // a child
      childToParent.putIfProbablyAbsent(context, parent);
      parentToChildCount.get(parent).incrementAndGet();
    }
    return true;
  }

  public boolean end(TraceContext context, MutableSpan span, Cause cause) {
    // Kick-out if this was not a normal finish
    if (cause != Cause.FINISHED && !context.isLocalRoot()) { // a child
      TraceContext parent = childToParent.remove(context);
      AtomicInteger childCount = parentToChildCount.getIfPresent(parent);
      if (childCount != null) childCount.decrementAndGet();
      return true;
    }

    AtomicInteger childCount = parentToChildCount.getIfPresent(context);
    span.tag("childCountDirect", childCount != null ? childCount.toString() : "0");

    // clean up so no OutOfMemoryException!
    childToParent.remove(context);
    parentToChildCount.remove(context);

    return true;
  }
}
```

The above is a partial implementation, the full code is [here](src/test/java/brave/features/handler/CountingChildrenTest.java).

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

  public void onSchedule(Attributes attributes) {
    // Stash the invoking trace context as this will be the correct parent of
    // the request.
    attributes.put(TraceContext.class, currentTraceContext.get());
  }

  public void onStart(Request request, Attributes attributes) {
    // Retrieve the parent, if any, and start the span.
    TraceContext parent = attributes.get(TraceContext.class);
    Span span = tracer.nextSpanWithParent(samplerFunction, request, parent);

    // add tags etc..

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
       .addScopeDecorator(ThreadContextScopeDecorator.get())
       .build())
    ...
    .build();
```

Besides logging, other tools are available. [StrictCurrentTraceContext](src/main/java/brave/propagation/StrictCurrentTraceContext.java) can
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
testing instrumentation, use [StrictCurrentTraceContext](src/main/java/brave/propagation/StrictCurrentTraceContext.java), as it will throw
errors on known scoping problems.

If you see data with the annotation `brave.flush`, you may have an
instrumentation bug. To see which code was involved, set
`Tracing.Builder.trackOrphans()` and ensure the logger `brave.Tracer` is at
'FINE' level. Do not do this in production as tracking orphaned data incurs
higher overhead.

Note: When using log4j2, set the following to ensure log settings apply:
`-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager`

## Unit testing instrumentation

When writing unit tests, there are a few tricks that will make bugs
easier to find:

* Report spans into a concurrent queue, so you can read them in tests
* Use `StrictCurrentTraceContext` to reveal subtle thread-related propagation bugs
* Unconditionally cleanup `Tracing.current()`, to prevent leaks

Here's an example setup for your unit test fixture:
```java
ConcurrentLinkedDeque<Span> spans = new ConcurrentLinkedDeque<>();

StrictCurrentTraceContext currentTraceContext = StrictCurrentTraceContext.create()
Tracing tracing = Tracing.newBuilder()
                 .currentTraceContext(currentTraceContext)
                 .spanReporter(spans::add)
                 .build();

  @After public void close() {
    Tracing current = Tracing.current();
    if (current != null) current.close();
    currentTraceContext.close();
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

## Rationale
See our [Rationale](RATIONALE.md) for design thoughts and acknowledgements.
