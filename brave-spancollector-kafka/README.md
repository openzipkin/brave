# brave-spancollector-kafka #

SpanCollector that is used to submit spans to Kafka.

Spans are sent to kafka as keyed messages: the key is the topic `zipkin` 
and the value is a TBinaryProtocol encoded Span.

## Monitoring ##

Monitor `KafkaSpanCollector`'s by providing your implementation of `SpanCollectorMetricsHandler`. It will
be notified when a span is accepted for processing and when it gets dropped for any reason, so you can update corresponding
counters in your metrics tool. When a span gets dropped, the reason is written to the application logs.
The number of spans sent to the target collector can be calculated by subtracting the dropped count from the accepted count.

Refer to `DropwizardMetricsScribeCollectorMetricsHandlerExample` for an example of how to integrate with
[dropwizard metrics](https://github.com/dropwizard/metrics).