# brave-instrumentation-cassandra
This contains tracing instrumentation for [Cassandra](https://github.com/apache/cassandra/blob/trunk/src/java/org/apache/cassandra/tracing/Tracing.java).

`brave.cassandra.Tracing` extracts trace state from the custom payload
of incoming requests. How long each request takes, each suboperation,
and relevant tags like the session ID are reported to Zipkin.

## Integration
Cassandra tracing is similar to Http tracing. It all works when both
sides agree on how to send and receive the trace context. This shows you
how to configure things most simply.

### Client
The server treats all inbound requests as new traces unless clients have
tracing setup. The easiest way to continue tracing from other applications
is to decorate your session with our [cassandra driver integration](../cassandra-driver).

For example:
```java
session = TracingSession.create(tracing, realSession);
```

### Server
For this to work, you must add classes to your cassandra classpath. It
then needs the system property "cassandra.custom_tracing_class" set to
"brave.cassandra.Tracing".

The easiest way to get started is to place this project's "all" jar in
cassandra's lib directory and start cassandra with java options like this:

```bash
$ JVM_OPTS='-Dzipkin.http_endpoint=http://localhost:9411/api/v1/spans -Dcassandra.custom_tracing_class=brave.cassandra.Tracing' cassandra
```

Note this jar is about 200KiB and does not include any classes besides
Brave and Zipkin. It has limited configuration to the below:

System property | Default | Description
--- | --- | ---
zipkin.http_endpoint | none | The url to Zipkin's POST endpoint. Ex. http://myhost:9411/api/v1/spans
zipkin.service_name | cassandra | The name that shows up in Zipkin's search and dependency graph

## Custom Integration

### Client
Clients must enable tracing. If they want to continue an existing trace,
they must add propagation fields to the custom payload of a statement.

```java
// minimally, you need to prepare a statement and enable tracing
preparedStatement.enableTracing();

// By default, B3 style is used, so instrumented clients do something like this
Map<String, ByteBuffer> payload = new LinkedHashMap<>();
payload.set("X-B3-TraceId", byteBuffer("463ac35c9f6413ad"));
payload.set("X-B3-ParentSpanId", byteBuffer("463ac35c9f6413ad"));
payload.set("X-B3-SpanId", byteBuffer("72485a3953bb6124"));
payload.set("X-B3-Sampled", byteBuffer("1"));
preparedStatement.setOutgoingPayload(payload);
```

## Server
Some may want to make a custom jar that includes explicit configuration
for their site's Zipkin service. Others may want to relegate that to yaml
or configuration. This plugin can use either an implicit `brave.Tracing`
component setup elsewhere in the JVM, or one explicitly called via the
constructor.
