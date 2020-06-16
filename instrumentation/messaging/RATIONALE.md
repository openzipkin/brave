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

### Why would someone tag message ID?
The message ID allows deletion, visibility control, retrieval, correlation, duplicate detection or a
combination of these use cases.

The purpose of the Message ID, as defined here, is a correlation between one "send" event and any
number of "receive" events. Not all operations can be tracked in one trace. Some consumers log, but
don't trace. Others break traces, or trace in different systems. Moreover, this project doesn't
trace all operations. With the message ID, processes curious about an ACK or NACK can find logs
related to those operations, even if the logs do not include trace IDs.

### What are some examples of message IDs?
We derive semantics by looking at multiple open source projects and cloud services, as well as the
special case of JMS.

| System      | Kind     | Operation      | Direction | Field                       | Owner  | Scope      | Format
|-------------|----------|----------------|-----------|-----------------------------|--------|------------|--------
| AMQP        | PRODUCER | publish        | Request   | message-id                  | Local  | Global     | 1-255 characters
| AMQP        | CONSUMER | consume        | Request   | message-id                  | Remote | Global     | 1-255 characters
| Azure Queue | PRODUCER | PutMessage     | Response  | MessageId                   | Remote | Global     | UUID
| Azure Queue | CONSUMER | DeleteMessage  | Response  | MessageId                   | Remote | Global     | UUID
| Artemis     | CONSUMER | receive        | Request   | messageId                   | Remote | Global     | random uint64
| AWS SQS     | PRODUCER | SendMessage    | Response  | MessageId                   | Remote | Global     | UUID
| AWS SQS     | CONSUMER | ReceiveMessage | Response  | MessageId                   | Remote | Global     | UUID
| AWS SNS     | PRODUCER | Publish        | Response  | MessageId                   | Remote | Global     | UUID
| AWS SNS     | CONSUMER | POST           | Request   | x-amz-sns-message-id        | Remote | Global     | UUID
| GCP PubSub  | PRODUCER | Publish        | Response  | message_id                  | Remote | Topic      | Integer
| GCP PubSub  | CONSUMER | Push           | Request   | message_id                  | Remote | Topic      | Integer
| GCP PubSub  | CONSUMER | Pull           | Response  | message_id                  | Remote | Topic      | Integer
| JMS         | PRODUCER | Send           | Response  | JMSMessageId                | Remote | Global     | ID:opaque string
| JMS         | CONSUMER | Receive        | Request   | JMSMessageId                | Remote | Global     | ID:opaque string
| Pulsar      | PRODUCER | Send           | Response  | MessageId                   | Remote | Topic      | bytes(ledger|entry|parition)
| Pulsar      | CONSUMER | Receive        | Request   | MessageId                   | Remote | Topic      | bytes(ledger|entry|parition)
| RocketMQ    | PRODUCER | send           | Response  | msgId                       | Remote | Topic      | HEX(ip|port|offset)
| RocketMQ    | CONSUMER | consumeMessage | Request   | msgId                       | Remote | Topic      | HEX(ip|port|offset)
| ServiceBus  | PRODUCER | POST           | Request   | BrokerProperties{MessageId} | Local  | Global     | 1-255 characters
| ServiceBus  | CONSUMER | DELETE         | Request   | BrokerProperties{MessageId} | Remote | Global     | 1-255 characters
| STOMP       | PRODUCER | MESSAGE        | Request   | message-id Header           | Local  | Connection | arbitrary
| STOMP       | CONSUMER | MESSAGE        | Request   | message-id Header           | Remote | Connection | arbitrary

### What are some examples of IDs that aren't message IDs?

The following fields are similar to message IDs, but are offset in nature, allowing message
processing to resume at a given point. We will have a different tag for these.

| System      | Kind     | Operation      | Direction | Field                       | Owner  | Scope      | Format
|-------------|----------|----------------|-----------|-----------------------------|--------|------------|--------
| Kafka       | PRODUCER | send           | Response  | offset                      | Remote | Topic      | uint64
| Kafka       | CONSUMER | poll           | Request   | offset                      | Remote | Topic      | uint64
| AWS Kinesis | PRODUCER | Publish        | Response  | SequenceNumber              | Remote | Stream     | 1-128 digits
| AWS Kinesis | CONSUMER | Lambda         | Request   | sequenceNumber              | Remote | Global     | 1-128 digits

The following fields give exactly once processing, but are independent of message ID or co-exist
them.

| System      | Kind     | Operation      | Direction | Field                       | Owner  | Scope      | Format
|-------------|----------|----------------|-----------|-----------------------------|--------|------------|--------
| AWS SQS     | PRODUCER | SendMessage    | Request   | MessageDeduplicationId      | Local  | Queue      | SHA-256(body)
| AWS SQS     | CONSUMER | ReceiveMessage | Request   | MessageDeduplicationId      | Remote | Queue      | SHA-256(body)
| MQTT        | PRODUCER | PUBLISH        | Request   | Packet Identifier           | Local  | Connection | uint16
| MQTT        | CONSUMER | PUBLISH        | Request   | Packet Identifier           | Remote | Connection | uint16
| MQTT        | PRODUCER | PUBACK/PUBREC  | Response  | Packet Identifier           | Local  | Connection | uint16
| MQTT        | CONSUMER | PUBACK/PUBREC  | Response  | Packet Identifier           | Remote | Connection | uint16

The following STOMP fields provide message acknowledgment featues, but are not classified as message IDs.

| System      | Kind     | Operation      | Direction | Field                       | Owner  | Scope      | Format
|-------------|----------|----------------|-----------|-----------------------------|--------|------------|--------
| STOMP       | PRODUCER | ACK/NACK       | Response  | id Header                   | Local  | Connection | arbitrary
| STOMP       | CONSUMER | ACK/NACK       | Response  | id Header                   | Remote | Connection | arbitrary
| STOMP       | PRODUCER | SEND           | Request   | receipt Header              | Local  | Connection | arbitrary
| STOMP       | PRODUCER | RECEIPT/ERROR  | Response  | receipt-id Header           | Local  | Connection | arbitrary
| STOMP       | CONSUMER | SEND           | Request   | receipt Header              | Remote | Connection | arbitrary
| STOMP       | CONSUMER | RECEIPT/ERROR  | Response  | receipt-id Header           | Remote | Connection | arbitrary

### Isn't correlation ID the same as a message ID?
A correlation ID is a system-wide lookup value that possibly can pass multiple steps. A message ID
can have a scope as small as one segment (Ex. flow from producer to broker), and typically an
implementation detail. It can be the case that they are the same, but it is not commonly the case.

For example in MQTT, the packet ID is only valid for one segment a message takes. This means for the
same message body, the packet ID is overwritten when passing from the producer to the eventual
consumer. A more extreme example is Artemis, where there is no api to receive the messageId
associated with a published message. In other words, it is only visible in the consumer side, so
cannot be used for correlation between the producer and consumer.

Another example are cloud services, such as Amazon SQS, Azure Queue and GCP PubSub. The message ID
fields there are set by the service and cannot be set by the client. This means that a client cannot
propagate the message ID from one part of a pipeline to another. Hence, in these cases a message ID
cannot be a correlation ID. The message ID will only be the same between the last producer and its
direct consumers.

Even though there's no strict relationship, message IDs are sometimes reused as correlation IDs. For
example, one pattern in JMS is to copy the incoming `JMSMessageID` as the `CorrelationID` in a
`JMSReplyTo` response. In this case, the same ID is used in different fields conventionally even if
not defined by the specification.

For the above reasons, we cannot use the message ID and correlation ID concepts interchangeably even
if there are sometimes overlaps in use cases.

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

Azure Queue includes a service-generated `MessageId`, but also a client-generated `x-ms-client-request-id`
for correlation. Unlike most services, Azure Queue has delete or update by `MessageId` functions,
though a `popreceipt` (akin to subscriber) is also required for these tasks.

Azure ServiceBus includes the AMQP `BrokerProperties{MessageId}` field, but it also supports the
Kafka protocol, which does not have a message ID. In support of the latter, it also has a
`SequenceId` field, which embeds parition information with a logical sequence. Note, when using
Kafka protocol an `offset` will also exist, and is not directly related to the `SequenceId`.

A Google PubSub Subscriber receives both a `message_id` and an `ack_id` in the `PullResponse`. The
`ack_id` has a different format and is scoped to the subscriber.

Pulsar has a client-generated `SequenceID`, but the broker controls the `MessageID` (sent in the
response). The `MessageID` is not derived from the `SequenceID` and they serve different purposes.
`SequenceID` is more about in-flight message tracking; consumer and admin apis use `MessageId` to
identify, ack and nack a message.

When used synchronously, `JMSProducer` cannot read the `JMSMessageId` field, as there's no result
type returned. Even when used asynchronously, there's a `setDisableMessageID` feature which disables
it. This means from call to call, `JMSMessageId` could be visible or not, even in the same library.

### Why not use offset/sequence when there's no message ID?
Kafka and MQTT don't define a field named message ID. When there isn't a field named message ID, we
considered a close stable value, specifically the as a sequence number or offset, as opposed to
returning `null`.

A sequence number or offset could clarify duplicate sends or trace context breaks in the same way
that a message ID could, even if it requires looking at other fields. It is true that many message
ID formats require looking at other fields anyway. A standard tag is easier to access as it requires
no library specific types to parse.

Ex. This requires just the messaging jar to express a policy that includes a message ID like field:
```java
MessagingTags.MESSAGE_ID.tag(req, context, span);
```

Ex. This requires messaging and Kafka instrumentation jars to tag the same:
```java
KafkaTags.OFFSET.tag(req, context, span);
```

However, we later decided that to make offset a standard field. Moreover, there are services, at
least Azure ServiceBus, that can return both a message ID and a sequence number. We decided to
formalize message ID as solely the field named as such as opposed to falling back to an offset field
on `null`.

### Why not synthesize a format that includes all needed fields when there's no message ID?
Typical message ID formats encode multiple components such as a broker ID or network address,
destination, timestamp or offset. It may be tempting to compose a format to include the dimensions
that likely pinpoint a message when there's no format defined by the library. For example, in Kafka,
we could compose a format like `topic-partition-offset` to ensure `MessagingRequest.id()` would have
all identifying information.

If we did that, we'd add overhead with the only consumer being tracing itself. It would fail as a
correlation field with other libraries as by definition our format would be bespoke. Moreover,
higher layers of abstraction which might have a defined message ID format could be confused with
ours. Later, if that same tool creates a message ID format, it would likely be different from ours.

For reasons including these, if there's no message ID field just return `null`.
