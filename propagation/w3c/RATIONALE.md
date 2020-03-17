# brave-propagation-w3c rationale

## Trace Context Specification

### Why do we write a tracestate entry?
We write both "traceparent" and a "tracestate" entry for two reasons. The first is due to
incompatibility between the "traceparent" format and our trace context. This is described in another
section.

The other reason is durability across hops. When your direct upstream is not the same tracing
system, its span ID (which they call parentId in section 3.2.2.4) will not be valid: using it will
break the trace. Instead, we look at our "tracestate" entry, which represents the last known place
in the same system.

### What is supported by Brave, but not by traceparent format?

#### `SamplingFlags` can precede a trace
B3 has always had the ability to influence the start of a trace with a sampling decision. For
example, you can propagate `b3=0` to force the next hop to not trace the request. This is used for
things like not sampling health checks. Conversely, you can force sampling by propagating `b3=d`
(the debug flag). `traceparent` requires a trace ID and a span ID, so cannot propagate this.

#### `TraceIdContext` (trace ID only)
Amazon trace format can propagate a trace ID prior to starting a trace, for correlation purposes.
`traceparent` requires a trace ID and a span ID, so cannot a trace ID standalone.

#### `TraceContext.sampled() == null`
B3 and Amazon formats support assigning a span ID prior to a sampling decision. `traceparent` has no
way to tell the difference between an explicit no and lack of a decision, as it only has one bit
flag.

#### `TraceContext.parentId()`
The parentId is a propagated field and also used in logging expressions. This is important for RPC
spans as downstream usually finishes before upstream. This obviates a data race even if Zipkin's UI
can tolerate lack of parent ID. What `traceparent` calls `parent-id` is not the parent, rather the
span ID. It has no field for the actual parent ID.

#### Debug flag
B3 has always had a debug flag, which is a way to force a trace even if normal sampling says no.
`traceparent` cannot distinguish between this and a normal decision, as it only has one bit flag.

#### Trace-scoped sampling decision
`traceparent` does not distinguish between a hop-level or a trace scoped decision in the format.
This means that traces can be broken as it is valid to change the decision at every step (which
breaks the hierarchy). This is the main reason why we need a separate `tracestate` entry.

### Why serialize the trace context in two formats?

The "traceparent" header is only portable to get the `TraceContext.traceId()` and
`TraceContext.spanId()`. Section 3.2.2.5.1, the sampled flag, is incompatible with B3 sampling. The
format also lacks fields for `TraceContext.parentId()` and `TraceContext.debug()`. This requires us
to re-serialize the same context in two formats: one for compliance ("traceparent") and one that
actually stores the context (B3 single format).

The impact on users will be higher overhead and confusion when comparing the sampled value of
"traceparent" which may be different than "b3".

### Why is traceparent incompatible with B3?

It may seem like incompatibility between "traceparent" and B3 were accidental, but that was not the
case. The Zipkin community held the first meetings leading to this specification, and were directly
involved in the initial design. Use cases of B3 were well known by working group members. Choices to
become incompatible with B3 (and Amazon X-Ray format) sampling were conscious, as were decisions to
omit other fields we use. These decisions were made in spite of protest from Zipkin community
members and others. There is a rationale document for the specification, but the working group has
so far not explained these decisions, or even mention B3 at all.

https://github.com/w3c/trace-context/blob/d2ed8084780efcedab7e60c48b06ca3c00bea55c/http_header_format_rationale.md
