# brave-spancollector-kafka #

SpanCollector that encodes spans into a thrift list, sent to the Kafka topic `zipkin`.

Kafka messages contain no key or partition, only a value which is a TBinaryProtocol encoded list of spans.

*Important*
If using zipkin-collector-service (or zipkin-receiver-kafka), you must run v1.35+

## Configuration ##

By default...

* Spans are flushed to a Kafka message every second. Configure with `KafkaSpanCollector.Config.flushInterval`.

## Monitoring ##

Monitor `KafkaSpanCollector`'s by providing your implementation of `SpanCollectorMetricsHandler`. It will
be notified when a span is accepted for processing and when it gets dropped for any reason, so you can update corresponding
counters in your metrics tool. When a span gets dropped, the reason is written to the application logs.
The number of spans sent to the target collector can be calculated by subtracting the dropped count from the accepted count.

Refer to `DropwizardMetricsSpanCollectorMetricsHandlerExample` for an example of how to integrate with
[dropwizard metrics](https://github.com/dropwizard/metrics).
