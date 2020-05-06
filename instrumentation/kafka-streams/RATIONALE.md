# brave-kafka-streams rationale

## What's happening?
Typically, there are at least two spans involved in traces produces by a Kafka Stream application:
* One created by the Consumers that starts a Stream or Table, by `builder.stream(topic)`.
* One created by the Producer that sends a records to a Stream, by `builder.to(topic)`

By receiving records in a Kafka Streams application with Tracing enabled, the span created, once
a record is received, will inject the span context on the headers of the Record, and it will get
propagated downstream onto the Stream topology. The span context is stored in the Record Headers,
the Producers at the middle (e.g. `builder.through(topic)`) or at the end of a Stream topology
will reference the initial span, and mark the end of a Stream Process.

If intermediate steps on the Stream topology require tracing, then `TracingProcessorSupplier` and
`TracingTransformerSupplier` will allow you to define a Processor/Transformer where execution is recorded as Span,
referencing the parent context stored on Headers, if available.

### Partitioning

Be aware that operations that require `builder.transformer(...)` will cause re-partitioning when
grouping or joining downstream ([Kafka docs](https://kafka.apache.org/documentation/streams/developer-guide/dsl-api.html#applying-processors-and-transformers-processor-api-integration)).

### Why doesn't this trace all Kafka Streams operations?

When starting to design this instrumentation, “trace everything” was the first idea:
When a message enters the Kafka Streams topology starts a new `poll` span, and every operation
(e.g. `map`, `filter`, `join`, etc.) is chained as an additional child span.

Kafka Streams materializes its topology _internally_ based on DSL operations.
Therefore, is not possible to hook into the topology creation process to instrument each operation.

We considered changing Kafka Streams to have hooks to do this, but it would be a hard sell.
The impact of adding these and rearrange lacking context would have a considerable impact surface.

Even if available, it would potentially expose excessive details as **all**
operations would be traced (while not all of them are interesting),
making traces harder to grok; and would probably create the need to support
functionality to **do not** trace some operations, requiring anyway changes to your code.

Given the current scenario, `KafkaStreamsTracing` is equipped with a set of common DSL operation wrapped as
Processors/Transformers APIs that enable tracing when needed;
apart from `poll` and `send` spans available out-of-the-box.
