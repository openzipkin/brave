# brave-spancollector-http #

SpanCollector that encodes spans into a json list, POSTed to `/api/v1/spans`.

## Configuration ##

By default...

* Spans are flushed to a POST request every second. Configure with `HttpSpanCollector.Config.flushInterval`.
* The POST body is not compressed. Configure with `HttpSpanCollector.Config.compressionEnabled`.
