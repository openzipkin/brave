# brave-instrumentation rationale

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
