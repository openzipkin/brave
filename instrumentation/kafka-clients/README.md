# Brave Kafka instrumentation

Add decorators for Kafka producer and consumer to enable tracing for Single Producer/Single Consumer (for now).
The producer propagates the B3 headers in the records headers.
The consumer is responsible to close the producer span.

To use the producer simply wrap it like this : 
```java
Producer<K, V> stringProducer = new KafkaProducer<>(settings);
TracingProducer<K, V> tracingProducer = kafkaTracing.producer(producer);
```

Same goes for the consumer : 
```java
Consumer<K, V> consumer = new KafkaConsumer<>(settings);
TracingConsumer<K, V> tracingConsumer = new TracingConsumer<>(consumer);
```

## Continue traces after consuming
Because Kafka batches messages while consuming, we flush every spans in the headers during poll.

If you wish to continue a trace you can use:
```java
Span s = RecordTracing.nexSpan(record);
```
and use the retrieved span as usual.

## Note
This tracer is only compatible with Kafka versions including headers support ( > 0.11.0).
