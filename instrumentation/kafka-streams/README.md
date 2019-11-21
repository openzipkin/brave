# Brave Kafka Streams instrumentation [EXPERIMENTAL]

Add decorators for Kafka Streams to enable tracing.
* `TracingKafkaClientSupplier` a client supplier to provide Producers and Consumers with tracing enabled (based on [kafka-clients](../kafka-clients) instrumentation).
* Processor/Transformers suppliers: 
    * `TracingProcessorSupplier`
    * `TracingTransformerSupplier`
    * `TracingValueTransformerSupplier`
    * `TracingValueTransformerWithKeySupplier`
* Kafka Streams operators:
    * `foreach()`
    * `peek()`
    * `mark()`
    * `map()`
    * `flatMap()`
    * `filter()`
    * `filterNot()`
    * `markAsFiltered()`
    * `mapValues()`
    
## Setup

First, setup the generic Kafka Streams component like this:
```java
import brave.kafka.streams.KafkaStreamsTracing;

//...

kafkaStreamsTracing = KafkaStreamsTracing.create(tracing);
```

To trace processors u operators:

```java
builder.stream(inputTopic)
       .trasformValues(kafkaStreamsTracing.valueTransformer("process", customTransformer))
       .processor(kafkaStreamsTracing.foreach("foreach", foreachFuncion));
```

To create a Kafka Streams instance with Tracing on Producers and Consumers:

```java
KafkaStreams kafkaStreams = kafkaStreamsTracing.kafkaStreams(topology, streamsConfig);
```

For more details, [see here](https://github.com/apache/incubator-zipkin-brave/blob/master/instrumentation/kafka-streams/src/main/java/brave/kafka/streams/KafkaStreamsTracing.java).

## What's happening?
Kafka Streams applications are based on Kafka Clients (Producer and Consumer API) to poll and send records
to Kafka Topics. By creating a Kafka Streams instance with Brave instrumentation, `poll` operation by 
Kafka Consumer, and `send` operations will be traced, creating a trace with inputs/outputs of a processing
pipeline. These 2 spans (or more, depending on how many `send` operations are running on your pipeline)
will mark the beginning and end of a record processed by Kafka Streams.

To map the operators executed along the way (`map`, `flatMap`, `filter`, etc.) operations can be
replaced by wrappers available on Kafka Streams Tracing API. This operators will extract context
from Record Headers and add more spans to map processing.

### Partitioning

Be aware that operations that require `builder.transformer(...)` will cause re-partitioning when
grouping or joining downstream ([Kafka docs](https://kafka.apache.org/documentation/streams/developer-guide/dsl-api.html#applying-processors-and-transformers-processor-api-integration)).

## Notes

* This tracer is only compatible with Kafka Streams versions including headers support ( > 2.0.0).
