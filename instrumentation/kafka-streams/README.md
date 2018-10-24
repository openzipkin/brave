# Brave Kafka Streams instrumentation

Add decorators for Kafka Streams to enable tracing.
* `TracingKafkaClientSupplier` a client supplier which traces poll and send operations.
* `TracingProcessorSupplier` completes a span on `process`
* `TracingTransformerSupplier` completes a span on `transform`

## Setup
First, setup the generic Kafka Streams component like this:
```java
kafkaStreamsTracing = KafkaStreamsTracing.create(kafkaTracing);
```

To add a Tracing Processor to your application use the `TracingProcessorSupplier` provided by instrumentation:
```java
builder.stream(inputTopic)
       .processor(kafkaStreamsTracing.processor(
            "forward-1",
            customProcessor));
```

To add a Tracing Transformer to your Stream, use the `TracingTransformerSupplier`, `TracingValueTransformerSupplier`, and `TracingValueTransformerWithValueSupplier` provided by instrumentation:
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
           "value-transformer-1",
           customTransformer))
       .to(outputTopic);
```

```java
builder.stream(inputTopic)
       .transformValueWithKey(kafkaStreamsTracing.valueTransformerWithKey(
           "value-transformer-with-key-1",
           customTransformer))
       .to(outputTopic);
```

To create a Kafka Streams with Tracing Client Supplier enabled pass your topology and configuration like this:
```java
KafkaStreams kafkaStreams = kafkaStreamsTracing.kafkaStreams(topology, streamsConfig);
```

## What's happening?
Typically, there are at least two spans involved in traces produces by a Kafka Stream application:
* One created by the Consumers that starts a Stream or Table, by `builder.stream(topic)`.
* One created by the Producer that sends a records to a Stream, by `builder.to(topic)`

By receiving records in a Kafka Streams application with Tracing enabled, the span created once
a record is received will inject the span context on the headers of the Record, and it will get
propagated downstream on the Stream topology. As span context is stored in the Record Headers, 
the Producers at the middle (e.g. `builder.through(topic)`) or at the end of a Stream topology
will reference the initial span, and mark the end of a Stream Process.

If intermediate steps on the Stream topology require tracing, then `TracingProcessorSupplier` and
`TracingTransformerSupplier` will allow you to define a Processor/Transformer where execution is recorded as Span, 
referencing the parent context stored on Headers, if available.

## Notes

* This tracer is only compatible with Kafka Streams versions including headers support ( > 2.0.0).