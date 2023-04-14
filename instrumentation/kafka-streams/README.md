# Brave Kafka Streams instrumentation [EXPERIMENTAL]

Add decorators for Kafka Streams to enable tracing.
* `TracingKafkaClientSupplier` a client supplier which traces poll and send operations.
* `TracingProcessorSupplier` completes a span on `process`
* `TracingTransformerSupplier` completes a span on `transform`

This does not trace all operations by default. See [RATIONALE.md] for why.

## Setup

First, setup the generic Kafka Streams component like this:
```java
import brave.kafka.streams.KafkaStreamsTracing;

...

kafkaStreamsTracing = KafkaStreamsTracing.create(tracing);
```

[KIP-820](https://cwiki.apache.org/confluence/display/KAFKA/KIP-820%3A+Extend+KStream+process+with+new+Processor+API)
introduces new processor APIs to the Kafka Streams DSL.
The following sections show how to instrument applications with the latest and previous APIs.

## Kafka Streams  >= v3.4.0

To trace a processor in your application use the `TracingV2ProcessorSupplier`, provided by instrumentation API:

```java
builder.stream(inputTopic)
       .process(kafkaStreamsTracing.process(
            "process",
            customProcessor));
```

or the `TracingV2FixedKeyProcessorSupplier`, provided by instrumentation API:

```java
builder.stream(inputTopic)
        .processValues(kafkaStreamsTracing.processValues(
            "process",
            customProcessor));
```

## Kafka Streams  < v3.4.0

To trace a processor in your application use `TracingProcessorSupplier`, provided by instrumentation API:

```java
builder.stream(inputTopic)
       .processor(kafkaStreamsTracing.processor(
            "process",
            customProcessor));
```

To trace a transformer, use `TracingTransformerSupplier`, `TracingValueTransformerSupplier`, or `TracingValueTransformerWithValueSupplier` provided by instrumentation API:

```java
builder.stream(inputTopic)
       .transform(kafkaStreamsTracing.transformer(
           "transformer-1",
           customTransformer))
       .to(outputTopic);
```

```java
builder.stream(inputTopic)
       .transformValue(kafkaStreamsTracing.valueTransformer(
           "transform-value",
           customTransformer))
       .to(outputTopic);
```

```java
builder.stream(inputTopic)
       .transformValueWithKey(kafkaStreamsTracing.valueTransformerWithKey(
           "transform-value-with-key",
           customTransformer))
       .to(outputTopic);
```

Additional transformers have been introduced to cover most common Kafka Streams DSL operations (e.g. `map`, `mapValues`, `foreach`, `peek`, `filter`).

```java
builder.stream(inputTopic)
       .transform(kafkaStreamsTracing.map("map", mapper))
       .to(outputTopic);
```

For flat operations like flatMap, the `flatTransform` method can be used:

```java
builder.stream(inputTopic)
       .flatTransform(kafkaStreamsTracing.flatMap("flat-map", mapper))
       .to(outputTopic);
```

For more details, [see here](https://github.com/openzipkin/brave/blob/master/instrumentation/kafka-streams/src/main/java/brave/kafka/streams/KafkaStreamsTracing.java).

To create a Kafka Streams with Tracing Client Supplier enabled, pass your topology and configuration like this:

```java
KafkaStreams kafkaStreams = kafkaStreamsTracing.kafkaStreams(topology, streamsConfig);
```

## Notes

* This tracer is only compatible with Kafka Streams versions including headers support ( > 2.0.0).
