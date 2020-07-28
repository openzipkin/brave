# brave-instrumentation-messaging rationale

## Why clear trace ID headers prior to invoking message processors?

When instrumentation intercept invocation of a message processor, they clear
trace ID headers (`Propagation.keys()`) before invoking a listener with a
span in scope. This is due to ordering precedence of tools like
`KafkaTracing.nextSpan(record)`.

Tools that extract incoming IDs from message requests prioritize headers
over the current span (`Tracer.currentSpan()`). This is to prevent an
accidentally leaked span (due to lack of `Scope.close()`) to become the parent
of all spans, creating a huge trace. This logic is the same in RPC server
instrumentation. When we are in control of invoking the message listener, we
can guarantee no mistakes by clearing the headers first.

We don't do this in server processing, even though Finagle used to to this.
There is no specific reason that we were never as defensive there, except that
we provide not tools like `ServletTracing.nextSpan(request)` because users
don't typically pass server requests through stages as they do with messaging.

One problem with clearing headers with JMS implementations. JMS requires you
to clear all headers before setting them, and there is no command to clear a
single header. This process is expensive as you have to read all the headers
first and replay them without the keys you want to key. An alternate approach
we have not tried is to set empty instead of the more difficult and fragile
"clear first" process. This could be used only in JMS, as other products have
the ability to clear headers, not just set them.

### Why don't we also clear baggage headers?
We don't clear baggage (ex. BaggagePropagation.allKeyNames) before invoking
processors as baggage headers don't interfere with trace hierarchy, and they
are often used by 3rd party code. For example, clearing a message header named
"user-id" or "request-id" prevents all code except that integrated with our
`BaggageField` api from seeing the values. We don't want to prevent layered
code from copying baggage-related properties into new messages.

## Why not `send-batch` and `receive-batch` operations?

Partitioning, indexing and aggregation of messaging traces are split by the
cardinality of operation names. Hence, we should be careful to only choose
operations that add value, and not choose ones that distract.

Batch operation names currently causes more confusion than they clear: on the
send side, batching may occur underneath and it could confuse users to suggest
it wasn't batched. Similarly, on the receive side, a batch could occur prior to
instrumentation that receive per-message callbacks.

Finally, instrumentation of batch size one is indistinguishable in flow than
single-message operations. Confusion about the Java API in use can be addressed
differently, such as with a "java.method" tag, identifying the simple name of
the type and the api instrumented: ex `Producer.send`.

For these reasons, we do not add batch operation names, rather stick with
"send" and "receive" with the same operation and default span name used
regardless of the count of messages handled in the request.

## Why share one consumer span for multiple uninstrumented messages?

In a batch instrumentation use case, we create one consumer span per distinct
incoming trace. The reason of this is to control overhead in cases of large
bulk.

For example, Kafka's `Consumer.poll` can by default accept 500 messages at
once. Generating 500 spans would slow down all messages until they are created.
More importantly, we would lose that all these messages shared the same root
entrypoint (`Consumer.poll`). In other words, we slow things down, but also
obfuscate what's happening in the case of new traces.

A trade off is that these 500 spans now share a root, and the trace could
become large if all downstream are handled differently. However, it can also be
the case that no message goes anywhere at all. Generating many traces
speculatively for an incoming bulk receive is considered more harmful than the
chance that a large trace might result. Moreover, there are means to address
larger traces. For example, recent versions of Zipkin Lens have a "re-root"
feature to break up large traces for display. Also, `Injector` code can be made
to intentionally break traces also. In summary, we do not speculatively break
bulk consumer operations into per-message traces. Instead we create one root
for all messages lacking an incoming context.

This implies that each message with an incoming context become a new consumer
child span. For example, if there were 150 messages and 145 had no trace state,
we have 6 consumer spans: one for the uninstrumented messages and one each to
continue incoming state.

Finally, this approach has been in use since late 2017, when we refined our
only messaging instrumentation, `kafka-clients`, to support message processing.
By re-using known working practice, we have less risk in abstraction.

