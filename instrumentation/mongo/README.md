# brave-instrumentation-mongo

This includes [`TraceMongoCommandListener`](src/main/java/brave/mongo/TraceMongoCommandListener.java), a
[`CommandListener`](https://mongodb.github.io/mongo-java-driver/3.12/driver/reference/monitoring/#command-monitoring)
for the Mongo Java driver that will report via Brave how long each command takes, along with relevant tags like the
database name, connection name, the command's operation (`insert`, `update`, `find`, etc.).

An application registers command listeners with a `MongoClient` by configuring `MongoClientSettings` as follows:

```java
CommandListener listener = TraceMongoCommandListener.builder()
        .tracer(Tracing.currentTracer())
        .build();
MongoClientSettings settings = MongoClientSettings.builder()
        .addCommandListener(listener)
        .build();
MongoClient client = MongoClients.create(settings);
```
