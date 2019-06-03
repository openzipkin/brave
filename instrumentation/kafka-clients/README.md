# Brave Kafka instrumentation

Add decorators for Kafka producer and consumer to enable tracing.
* `TracingProducer` completes a producer span per record and propagates it via headers.
* `TracingConsumer` completes a consumer span on `poll`, resuming a trace in headers if present.

## Setup
First, setup the generic Kafka component like this:
```java
kafkaTracing = KafkaTracing.newBuilder(tracing)
                           .writeB3SingleFormat(true) // for more efficient propagation
                           .remoteServiceName("my-broker")
                           .build();
```

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

## What's happening?
Typically, there are three spans involved in message tracing:
* If a message producer is traced, it completes a PRODUCER span per record
* If a consumer is traced, poll completes a CONSUMER span based on the incoming record or topic
* Message processors use `KafkaTracing.nextSpan` to create a span representing work

Similar to http, the trace context is propagated using headers. Unlike http, processing a message is
decoupled from consuming it. For example, `Consumer.poll` receives messages in bulk, from
potentially multiple topics at the same time. Instead of receiving one message like an http request,
`Consumer.poll` can receive up to 500 by default. For this reason, a single CONSUMER span is shared
for each invocation of poll when there is no incoming trace context. This span is the parent when
`KafkaTracing.nextSpan` is later called.

## Processing messages

When ready for processing use `KafkaTracing.nextSpan` to continue the trace.

```java
// Typically, poll is in a loop. the consumer is wrapped
while (running) {
  ConsumerRecords<K, V> records = tracingConsumer.poll(1000);
  // either automatically or manually wrap your real process() method to use kafkaTracing.nextSpan()
  records.forEach(record -> process(record));
  consumer.commitSync();
}
```

Since kafka-clients doesn't ship with a processing abstraction, we can't bake-in means to
automatically trace message processors. Guessing how one might make a message processor library
isn't great, as we'd not know what the signature is, if there are checked exceptions, if it is async
or not. If you are using a common library, you may want to raise an issue so that you don't need to
make custom instrumentation.

If you are in a position where you have a custom processing loop, you can do something like this
to trace manually or you can do similar via automatic instrumentation like AspectJ.
```java
<K, V> void process(ConsumerRecords<K, V> record) {
  // Grab any span from the record. The topic and key are automatically tagged
  Span span = kafkaTracing.nextSpan(record).name("process").start();

  // Below is the same setup as any synchronous tracing
  try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) { // so logging can see trace ID
    return doProcess(record); // do the actual work
  } catch (RuntimeException | Error e) {
    span.error(e); // make sure any error gets into the span before it is finished
    throw e;
  } finally {
    span.finish(); // ensure the span representing this processing completes.
  }
}
```

## Notes
* This tracer is only compatible with Kafka versions including headers support ( > 0.11.0).
* More information about "Message Tracing" [here](https://github.com/apache/incubator-zipkin-website/blob/master/pages/instrumenting.md#message-tracing)
