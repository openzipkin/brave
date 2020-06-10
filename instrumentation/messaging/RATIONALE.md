# brave-instrumentation-messaging rationale

## Why clear trace state headers prior to invoking message processors?

When instrumentation intercept invocation of a message processor, they clear
trace state headers (`Propagation.keys()`) before invoking a listener with a
span in scope. This is due to ordering precedence of tools like
`KafkaTracing.nextSpan(record)`.

Tools that extract incoming state from message requests prioritize headers
over the current span (`Tracer.currentSpan()`). This is to prevent an
accidentally leaked span (due to lack of `Scope.close()`) to become the parent
of all spans, creating a huge trace. This logic is the same in RPC server
instrumentation. When we are in control of invoking the message listener, we
can guarantee no mistakes by clearing the headers first.

We don't do this in server processing, eventhough Finagle used to to this.
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

## Message ID
In practice, the message ID is used for retrieval, correlation, duplicate detection or a combination
of these. We derive semantics by looking at multiple open source projects and cloud services, as
well the special case of JMS.

| System     | Kind     | Operation      | Direction | Field                  | Owner  | Scope      | Format
|------------|----------|----------------|-----------|------------------------|--------|------------|--------
| AMQP       | PRODUCER | publish        | Request   | message-id             | Local  | Global     | 1-255 characters
| AMQP       | CONSUMER | consume        | Request   | message-id             | Remote | Global     | 1-255 characters
| Artemis    | CONSUMER | receive        | Request   | messageId              | Remote | Global     | random uint64
| AWS SQS    | PRODUCER | SendMessage    | Request   | MessageDeduplicationId | Local  | Queue      | SHA-256(body)
| AWS SQS    | CONSUMER | ReceiveMessage | Request   | MessageDeduplicationId | Remote | Queue      | SHA-256(body)
| AWS SQS    | PRODUCER | SendMessage    | Response  | MessageId              | Remote | Global     | UUID
| AWS SQS    | CONSUMER | ReceiveMessage | Response  | MessageId              | Remote | Global     | UUID
| JMS        | PRODUCER | Send           | Response  | JMSMessageId           | Remote | Global     | ID:opaque string
| JMS        | CONSUMER | Receive        | Request   | JMSMessageId           | Remote | Global     | ID:opaque string
| Pulsar     | PRODUCER | Send           | Response  | MessageId              | Remote | Topic      | bytes(ledger|entry|parition)
| Pulsar     | CONSUMER | Receive        | Request   | MessageId              | Remote | Topic      | bytes(ledger|entry|parition)
| RocketMQ   | PRODUCER | send           | Response  | SendResult.msgId       | Remote | Topic      | HEX(ip|port|offset)
| RocketMQ   | CONSUMER | consumeMessage | Request   | MessageExt.msgId       | Remote | Topic      | HEX(ip|port|offset)
| MQTT       | PRODUCER | PUBLISH        | Request   | Packet Identifier      | Local  | Connection | uint16
| MQTT       | PRODUCER | PUBACK/PUBREC  | Response  | Packet Identifier      | Local  | Connection | uint16
| MQTT       | CONSUMER | PUBLISH        | Request   | Packet Identifier      | Remote | Connection | uint16
| MQTT       | CONSUMER | PUBACK/PUBREC  | Response  | Packet Identifier      | Remote | Connection | uint16
| STOMP      | PRODUCER | SEND           | Request   | receipt Header         | Local  | Connection | arbitrary
| STOMP      | PRODUCER | RECEIPT/ERROR  | Response  | receipt-id Header      | Local  | Connection | arbitrary
| STOMP      | CONSUMER | SEND           | Request   | receipt Header         | Remote | Connection | arbitrary
| STOMP      | CONSUMER | RECEIPT/ERROR  | Response  | receipt-id Header      | Remote | Connection | arbitrary
| STOMP      | PRODUCER | MESSAGE        | Request   | message-id Header      | Local  | Connection | arbitrary
| STOMP      | PRODUCER | ACK/NACK       | Response  | id Header              | Local  | Connection | arbitrary
| STOMP      | CONSUMER | MESSAGE        | Request   | message-id Header      | Remote | Connection | arbitrary
| STOMP      | CONSUMER | ACK/NACK       | Response  | id Header              | Remote | Connection | arbitrary

### Isn't correlation ID the same as a message ID
A correlation ID is a system-wide lookup value that possibly can pass multiple steps. A message ID
can have a scope as small as one segment (Ex. flow from producer to broker), and typically an
implementation detail. It can bet the case that they are the same, but it is not commonly the case.

For example in MQTT, the packet ID is only valid for one segment a message takes. This means for the
same message body, the packet ID is overwritten when passing from the producer to the eventual
consumer. A more extreme example is Artemis, where there is no api to receive the messageId
associated with a published message. In other words, it is only visible in the consumer side, so
cannot be used for correlation between the producer and consumer.

That said, correlation IDs are sometimes used for single-segment processing. For example, one
pattern in JMS is to copy the incoming `JMSMessageID` as the `CorrelationID` in a `JMSReplyTo`
response, so that the sender can correlate the two.

For the above reasons, we cannot use the message ID and correlation ID concepts interchangeably.

### When is a message ID ambiguous?
There are many types of features supported by message IDs. Some protocols use a global ID for
multiple features. Others use separate ones. In some cases, the choice of which to use for the
message ID field borders on arbitrary. Here are some examples to reinforce this.

Amazon SQS includes a service-generated `MessageId` that can identify a message later consumed.
However, to delete an instance of that message you need one of potentially many `ReceiptHandle`s
associated with the `MessageId`. The client can also set certain IDs. For example, a client sets
`MessageDeduplicationId` before sending a message to a FIFO queue to suppress redundant sends. In
other words there are at least 3 identifiers for a single message, in different formats, depending
on the use case.

Pulsar has a client-generated `SequenceID`, but the broker controls the `MessageID` (sent in the
response). The `MessageID` is not derived from the `SequenceID` and they serve different purposes.
`SequenceID` is more about in-flight message tracking; consumer and admin apis use `MessageId` to
identify, ack and nack a message.

### Why don't we define a message ID for tools lacking one, such as Kafka?

Typical message ID formats encode multiple components such as a broker ID or network address,
destination, timestamp or offset. It may be tempting to compose a format to include the dimensions
that likely pinpoint a message when there's no format defined by the library. For example, in Kafka,
we could compose a format like `topic-partition-offset` to ensure `MessagingRequest.id()` would not
be `null`, and people can access not-yet-standard fields such as partition or offset.

If we did that, we'd add overhead with the only consumer being tracing itself. It would fail as a
correlation field with other libraries as by definition our format would be bespoke. Moreover,
higher layers of abstraction which might have a defined message ID format could be confused with
ours. Later, if that same tool creates a message ID format, it would likely be different than ours.

For reasons including these, if there's no canonical format, we opt out of synthesizing a message ID
and just return `null`.
