# Brave Kafka instrumentation

Add decorators for Kafka producer and consumer to enable tracing for Single Producer/Single Consumer (for now).
The producer propagates the B3 headers in the records headers.
The consumer is responsible to close the producer span.

To use the producer simply wrap it like this : 
```java
Producer<String, String> stringProducer = new KafkaProducer<>(settings);
TracingProducer<String, String> tracingProducer = new TracingProducer<>(tracing, mockProducer);
```

Same goes for the consumer : 
```java
Consumer<String, String> consumer = new KafkaConsumer<>(settings);
TracingConsumer<String, String> tracingConsumer = new TracingConsumer<>(tracing, consumer);
```

## Continue traces after consuming
Because Kafka batches messages while consuming, we flush every spans in the headers during poll.

If you wish to continue a trace you can use:
```java
Span s = RecordTracing.nexSpanFromRecord(ConsumerRecord);
```
and use the retrieved span as usual.