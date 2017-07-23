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