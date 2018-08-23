# Brave Kafka Streams instrumentation

Add decorators for Kafka Streams client to enable tracing.
* `TracingKafkaClientSupplier` a client supplier which traces send and receive operations.

## Setup
First, setup [our Kafka Client instrumentation](../kafka-clients/README.md) like this:
```java
kafkaTracing = KafkaTracing.newBuilder(tracing)
                           .remoteServiceName("my-broker")
                           .build();
```

Next, create a streams component like this:
```java
kafkaStreamsTracing = KafkaStreamsTracing.create(kafkaTracing);
```

Use this component when building Kafka streams from a topology:
```java
kafkaStreams = new KafkaStreams(topology, properties, kafkaStreamsTracing.kafkaClientSupplier());
```
