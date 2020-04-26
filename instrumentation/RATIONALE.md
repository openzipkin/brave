# brave-instrumentation rationale
Rationale here applies to common decisions made in this directory. See
[Brave's RATIONALE](../brave/RATIONALE.md) for internal rationale.

## Why does the client response callback run in the invocation context?
This rationale applies equally to CLIENT and PRODUCER spans.

Asynchronous code is often modeled in terms of callbacks. For example, the
following pseudo code represents a chain of 3 client calls.
```java
// Assume you are reactive: assembling a call doesn't invoke it.
call = client.call("1")
             .flatMap((r) -> client.call("2"))
             .flatMap((r) -> client.call("3"));

ScopedSpan parent = tracer.startScopedSpan("parent");
try {
  // In reactive style, subscribe attaches the trace context
  call.subscribe(subscriber);
} finally {
  parent.finish();
}
```

It might be surprising that calls "2" and "3" execute in the "parent" trace
context, as opposed to the preceding client call. Put another way, response
callbacks run in the invocation context, which cause new spans to appear as
as a siblings, as opposed to children of the previous callback (in this case
a client).

This may sound unintuitive to those thinking in terms of callback nesting depth,
but having a consistent structure allows traces to appear similar regardless of
imperative vs async invocation. It also is more easy to reason with, but we'll
touch on that later.

Let's consider the above async pseudo code with the logical equivalent in
synchronous code. In each case, there are 3 client calls made in sequence. In
each case, there's a potential data dependency, but it isn't actually used!
```java
// synchronous
ScopedSpan parent = tracer.startScopedSpan("parent");
try {
  client.call("1");
  client.call("2");
  client.call("3");
} finally {
  parent.finish();
}

// reactive
call = client.call("1")
             .flatMap((r) -> client.call("2"))
             .flatMap((r) -> client.call("3"));

ScopedSpan parent = tracer.startScopedSpan("parent");
try {
  call.subscribe(subscriber);
} finally {
  parent.finish();
}
```

We mention that data is ignored to highlight one deduction one can make, which
is that the hierarchy should represent data dependency, as opposed to logical
or time wise. While this is interesting, it is difficult to execute in
practice. Instrumentation are usually at a lower level than the application
code that they run. Hierarchy is already chosen before it would know if data
would be read or not. In most cases, it would be unknowable if data were read
or used at that level of abstraction. In other words, such a relationship is
more fitting for span tags at a higher level, and decoupled from span
hierarchy.

Even throwing out the data dependency argument, some may think why not model
callback depth anyway? We should model spans how the code looks, right?

Only three calls may not seem that bad. Perhaps it is easy to reason with
what's going on. However, what if there were 100 or 1000? It would be very
difficult to reason with the actual parent which may be 999 levels up the tree.
Some backend code perform operations like counting children, in order to
determine fan out counts. This code would become useless as there would only
ever be one child! Put visually, imagine clicking '-' 999 times to find the
real parent in a typical trace UI!

We acknowledge that using the invocation context as the parent of follow-up
requests (response callback) is imperfect. It means any data dependency between
one response and the next request is not represented in the hierarchy. It also
means callback depth with not manifest in the trace hierarchy. That said,
follow-up requests still share not just the same trace, but also the same local
root, and also direct parent. As the clocks are the same (and in fact locked
against skew), the happens after relationship manifests in span timing. At any
rate, if a data dependency is important, you can consider mapping it as a tag.

This rationale was first mentioned in the below javascript issue.
https://github.com/openzipkin/zipkin-js/issues/176#issuecomment-355636603

It was later built upon during an [Armeria](https://github.com/line/armeria) pow-wow:
https://github.com/openzipkin/openzipkin.github.io/wiki/2018-08-06-Zipkin-and-Armeria-at-LINE-Fukuoka#teaching-brave-to-use-armerias-requestcontext

## Calling `Span.finish()` while the context is in scope
Instrumentation should call `Span.finish()` with the same context in scope as
opposed to clearing or using a different span context.

It may seem that clearing it is a better choice, but that actually causes
overhead as there are often decorators attached, which do things like log
property synchronization.

It may also seem that this could lead to accidental tracing to Zipkin itself.
This is possible. If a library that itself is instrumented with Brave is used
as a `Sender`, the application trace could continue into Zipkin. However, this
is also possible even if the context was reset to null. A traced client
encountering a cleared context would start a new trace, subject to its sampling
policy.

The only thing that resetting to cleared context would do vs not clearing is
about continuation. If the context isn't cleared, the application trace would
continue into Zipkin: any spans about span collection and storage will attach
to the application trace. Note that this would only occur if the server is set
to `SELF_TRACING_ENABLED=true` as tracing isn't enabled any other way.

`SELF_TRACING_ENABLED` isn't a usual configuration, and trace continuation can
be easily prevented by client configuration. For example, no packaged sender in
`zipkin-reporter` inherits tracing configuration. Moreover, all recommended
patterns of span reporting involve `AsyncReporter`, which already breaks traces
with its internal thread. In other words, accidentally passing trace headers is
only a risk in 3rd party reporters. These third parties can easily avoid the
concern by clearing headers from `Propagation.keys()` or using raw, untraced
clients.

In summary, we don't clear trace context to save overhead, and accept a small
risk of confusion where application traces have Zipkin spans in them when the
server has `SELF_TRACING_ENABLED=true` and a 3rd party traced reporter is in
use.
