# brave-spancollector-kafka #

SpanCollector that is used to submit spans to Kafka.

Spans are sent to kafka as keyed messages: the key is the topic `zipkin` 
and the value is a TBinaryProtocol encoded Span.