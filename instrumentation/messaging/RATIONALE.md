# brave-instrumentation-messaging rationale

## Why not `send-batch` and `receive-batch` operations?

The choices of operation names to promote split partitioning and indexing of
messaging traces. Hence, we should be careful to only choose operations that
add value, and not choose ones that distract.

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
