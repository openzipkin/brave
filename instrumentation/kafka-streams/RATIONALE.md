# brave-instrumentation-kafka-streams rationale

## What's happening?
Typically, there are at least two spans involved in traces produces by a Kafka Stream application:
* One created by the Consumers that starts a Stream or Table, by `builder.stream(topic)`.
* One created by the Producer that sends a record to a Stream, by `builder.to(topic)`

By receiving records in a Kafka Streams application with Tracing enabled, the span created, once
a record is received, will inject the span context on the headers of the Record, and it will get
propagated downstream onto the Stream topology. The span context is stored in the Record Headers,
the Producers at the middle (e.g. `builder.through(topic)`) or at the end of a Stream topology
will reference the initial span, and mark the end of a Stream Process.

If intermediate steps on the Stream topology require tracing, `TracingProcessorSupplier` and
`TracingTransformerSupplier` record execution into a new Span,
referencing the parent context stored on Headers, if available.

### Transformers and Partitioning

The behaviour of some operations wrapped into Kafka Streams Processor API types could change the underlying topology.

For example, `filter` operation on the Kafka Streams DSL is stateless and doesn't impact partitioning;
but `kafkaStreamsTracing.filter()` returns a `Transformer` that if grouping or joining operations
follows, it could lead to **unintentional partitioning**.

Be aware operations that any usage of `builder.transformer(...)` will cause re-partitioning when
grouping or joining downstream ([Kafka docs](https://kafka.apache.org/documentation/streams/developer-guide/dsl-api.html#applying-processors-and-transformers-processor-api-integration)).

### Why doesn't this trace all Kafka Streams operations?

When starting to design this instrumentation, “trace everything” was the first idea:
When a message enters the Kafka Streams topology starts a new `poll` span, and every operation
(e.g. `map`, `filter`, `join`, etc.) is chained as an additional child span.

Kafka Streams materializes its topology _internally_ based on DSL operations.
Therefore, is not possible to hook into the topology creation process to instrument each operation.

We considered changing Kafka Streams to have hooks to do this, but it would be a hard sell.
Adding these and rearrange lacking context would have a considerable library impact.

Even if we had hooks, tracing all operations would be excessive. The resulting large
traces would be harder to understand, leading to requests to disable tracing. The
code involved to disable tracing may mean more code than visa versa!

Given the current scenario, `KafkaStreamsTracing` is equipped with a set of common DSL operation wrapped as
Processors/Transformers APIs that enable tracing when needed;
apart from `poll` and `send` spans available out-of-the-box.
