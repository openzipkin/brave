# brave-instrumentation-mongodb

This includes [`TraceMongoCommandListener`](src/main/java/brave/mongodb/TraceMongoCommandListener.java), a
[`CommandListener`](https://mongodb.github.io/mongo-java-driver/3.12/driver/reference/monitoring/#command-monitoring)
for the Mongo Java driver that will report via Brave how long each command takes, along with relevant tags like the
collection/view name, the command's name (`insert`, `update`, `find`, etc.).

This instrumentation can only be used with the synchronous MongoDB driver. Do not use it with the asynchronous or
reactive drivers as tracing data will be incorrect.

## Span properties:
- `name`: command name (and collection/view name, if available). Examples: `find myCollection`, `listCollections`, etc.
- `kind`: `CLIENT`
- `remoteServiceName`: `mongodb-${databaseName}`. Example: `mongodb-myDatabase`.
- `remoteIpAndPort`: the IP address and port number of the MongoDB server that the command was issued to
- `error`: `Throwable` in case of failed command
- Tags:
  - `mongodb.command.name`: the name of the MongoDB command. Examples: `find`, `listCollections`, etc.
  - `mongodb.command`: a possibly truncated, JSON version of the full command. Example:
    `{"find": "myCollection", "filter": { "myField": { "$gte": 5 } } }`"
  - `mongodb.collection`: the name of the MongoDB collection/view that the command operates on, if available
  - `mongodb.cluster.id`: a client-generated identifier that uniquely identifies a connection to a MongoDB cluster

## Usage

An application registers command listeners with a `MongoClient` by configuring `MongoClientSettings` as follows:

```java
CommandListener listener = MongoDBTracing.create(Tracing.current())
        .commandListener();
MongoClientSettings settings = MongoClientSettings.builder()
        .addCommandListener(listener)
        .build();
MongoClient client = MongoClients.create(settings);
```
