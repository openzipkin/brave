# Brave Kafka Streams instrumentation [EXPERIMENTAL]

Add decorators for Kafka Streams to enable tracing.
* `KafkaStreamsTracing` completes a span on `process` or `processValues`

This does not trace all operations by default. See [RATIONALE.md] for why.

## Setup

First, setup the generic Kafka Streams component like this:
```java
import brave.kafka.streams.KafkaStreamsTracing;

...

kafkaStreamsTracing = KafkaStreamsTracing.create(tracing);
```

[KIP-820](https://cwiki.apache.org/confluence/display/KAFKA/KIP-820%3A+Extend+KStream+process+with+new+Processor+API)
introduces new processor APIs to the Kafka Streams DSL. You must use version >= v3.4.0
to instrument applications.

To trace a processor in your application use `kafkaStreamsTracing.process` like so:

```java
builder.stream(inputTopic)
       .process(kafkaStreamsTracing.process(
            "process",
            customProcessor));
```

or use `kafkaStreamsTracing.processValues` like so:

```java
builder.stream(inputTopic)
        .processValues(kafkaStreamsTracing.processValues(
            "processValues",
            customProcessor));
```

For more details, [see here](https://github.com/openzipkin/brave/blob/master/instrumentation/kafka-streams/src/main/java/brave/kafka/streams/KafkaStreamsTracing.java).

To create a Kafka Streams with Tracing Client Supplier enabled, pass your topology and configuration like this:

```java
KafkaStreams kafkaStreams = kafkaStreamsTracing.kafkaStreams(topology, streamsConfig);
```

## Notes

* This tracer is only compatible with Kafka Streams versions including headers support ( > 2.0.0).
