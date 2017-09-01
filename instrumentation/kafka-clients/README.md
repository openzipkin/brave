# Brave Kafka instrumentation

Add decorators for Kafka producer and consumer to enable tracing.
The producer add the propagation headers to the records and create an async "ms" span child of the current trace.
The consumer is responsible to create a new span "mr" span child of the header trace.

To use the producer simply wrap it like this : 
```java
Producer<K, V> stringProducer = new KafkaProducer<>(settings);
TracingProducer<K, V> tracingProducer = kafkaTracing.producer(producer);
tracingProducer.send(new ProducerRecord<K, V>("my-topic", key, value));
```

Same goes for the consumer : 
```java
Consumer<K, V> consumer = new KafkaConsumer<>(settings);
TracingConsumer<K, V> tracingConsumer = kafkaTracing.consumer(consumer);
tracingConsumer.poll(10);
```

Because Kafka batches messages while consuming, we create the new span from the headers during poll.
The new span then replace the previous record headers with the new propagation headers.
When processing a message later, you can continue the trace like this:
```java
Span forProcessor = kafkaTracing.joinSpan(record);
```
and use the retrieved span as usual.

## Notes
* This tracer is only compatible with Kafka versions including headers support ( > 0.11.0).
* More information about "Message Tracing" [here](https://github.com/openzipkin/openzipkin.github.io/blob/master/pages/instrumenting.md#message-tracing)
