# brave-spancollector-local #

Brave SpanCollector that submits spans to a local storage component.

For example, if you are tracing a storage system like cassandra,
you might prefer to write directly to cassandra as opposed to over http.

## Configuration ##

By default...

* Spans are flushed to a POST request every second. Configure with `LocalSpanCollector.Config.flushInterval`.
