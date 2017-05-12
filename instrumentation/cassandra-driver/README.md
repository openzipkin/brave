# brave-instrumentation-cassandra-driver
This contains tracing instrumentation for the [DataStax Java Driver](https://github.com/datastax/java-driver).

`brave.cassandra.driver.TracingSession` tracks the client-side of cassandra
and adds trace context to the custom payload of outgoing requests. If
[server integration](../cassandra) is in place, cassandra will contribute
data to these RPC spans.

To set this up, wrap your session like below
```java
session = TracingSession.create(tracing, realSession);
```


## Tagging policy
By default, the following are added to cassandra client spans:
* Span.name as the simple type-name of the statement: ex "bound-statement"
* Tags/binary annotations:
  * "cassandra.keyspace"
  * "cassandra.query" CQL of prepared statements
  * "error" when there is an error of any kind
* Remote IP and port information

To change the span and tag naming policy, you can do something like this:

```java
cassandraDriverTracing = cassandraDriverTracing.toBuilder()
    .parser(new CassandraParser() {
        @Override public String spanName(Statement statement) {
          return "query";
        }

        @Override public void requestTags(Statement statement, SpanCustomizer customizer) {
          super.requestTags(statement, tagger);
          customizer.tag("cassandra.fetch_size", Integer.toString(statement.getFetchSize()));
        }
    })
    .build();

tracesSession = TracingSession.create(cassandraDriverTracing.clientOf("remote-cluster"), session);
```

## Sampling Policy
The default sampling policy is to use the default (trace ID) sampler.

For example, if there's no trace already in progress, the sampler
indicated by `Tracing.Builder.sampler` decides whether or not to start a
new trace for the cassandra client request.

You can change the sampling policy by specifying it in the `CassandraDriverTracing`
component. Here's an example which only starts new traces for bound statements.

```java
cassandraDriverTracing = cassandraDriverTracing.toBuilder()
    .sampler(new CassandraDriverSampler() {
       @Override public Boolean trySample(Statement statement) {
         return statement instanceof BoundStatement;
       }
     })
    .build();
```
