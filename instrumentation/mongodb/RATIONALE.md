# Implementation notes

## Implementation notes regarding the *synchronous* MongoDB clients

Synchronous MongoDB clients: `com.mongodb.MongoClient` and `com.mongodb.client.MongoClient`.

It is sufficient to use `ThreadLocalSpan` because every command starts and ends on the same thread.

Most commands are executed in the thread where the `MongoClient` methods are called from, so (assuming that the tracing
context is correctly propagated to that thread) all spans should have the correct parent.

There are two exceptions to the above rule. Some maintenance operations are done on background threads:
 * [cursor cleaning](https://github.com/mongodb/mongo-java-driver/blob/67c9f738ae44bc15befb822644e7266634c7dcf5/driver-legacy/src/main/com/mongodb/MongoClient.java#L802)
 * [connection pool maintenance](https://github.com/mongodb/mongo-java-driver/blob/67c9f738ae44bc15befb822644e7266634c7dcf5/driver-core/src/main/com/mongodb/internal/connection/DefaultConnectionPool.java#L95).

The spans resulting from these maintenance operations will not have a parent span.

## Implementation notes regarding the *asynchronous* MongoDB clients

Asynchronous MongoDB clients: `com.mongodb.async.MongoClient` and `com.mongodb.reactivestreams.client.MongoClient`.

Support for asynchronous clients is **unimplemented**.

The asynchronous clients use threads for the async completion handlers (meaning that
`#commandStarted(CommandStartedEvent)` and `#commandSucceeded(CommandSucceededEvent)`/
`#commandFailed(CommandFailedEvent)` may get called from background threads and also not necessarily from the same
thread).

It should be possible to set a custom `com.mongodb.connection.StreamFactoryFactory` on the
`com.mongodb.MongoClientSettings.Builder` which can propagate the tracing context correctly between those handlers,
but this is **unimplemented** and it is unknown if this would be sufficient.
