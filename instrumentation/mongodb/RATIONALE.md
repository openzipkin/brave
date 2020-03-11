# brave-instrumentation-mongodb rationale

## Default data policy
We tried to make the default data policy similar to other instrumentation, such as MySQL, and also current practice
from existing sites. Like other instrumentation, the policy is intentionally conservative, in efforts to avoid large
spans and taxing sites that may not be interested in all fields. Site-specific overrides should be supported in a later
revision.

### Tag naming convention
An attempt was made to name the tags (such as `mongodb.cluster_id` and `mongodb.command`) similarly as in
[MongoMetricsCommandListener](https://github.com/micrometer-metrics/micrometer/blob/master/micrometer-core/src/main/java/io/micrometer/core/instrument/binder/mongodb/MongoMetricsCommandListener.java),
however, underscores were used instead of dots to avoid nested dots.

### Why not command BSON?
We considered adding the full command text: some users are logging to console and won't be able to use normal
correlation to retrieve the query text. Adding this by default would be troublesome for reasons of size and privacy.
For example, we don't tag "http.url" by default for reasons of privacy. In addition to this concern, the tagging BSON
could easily be large enough to break the process. For example, this is why Expedia have a different server,
haystack-blobs, to handle request uploads (spans only include links to data). In any case, trying to store the full
text would lead to a truncation concern. To simplify the first release, we leave out request tagging and plan to permit
users to do this on their own with a future Parser feature.

## Why do we use `ThreadLocalSpan` in synchronous instrumentation?
Synchronous MongoDB clients: `com.mongodb.MongoClient` and `com.mongodb.client.MongoClient`.

It is sufficient to use `ThreadLocalSpan` because every command starts and ends on the same thread.

Most commands are executed in the thread where the `MongoClient` methods are called from, so (assuming that the tracing
context is correctly propagated to that thread) all spans should have the correct parent.

There are two exceptions to the above rule. Some maintenance operations are done on background threads:
 * [cursor cleaning](https://github.com/mongodb/mongo-java-driver/blob/67c9f738ae44bc15befb822644e7266634c7dcf5/driver-legacy/src/main/com/mongodb/MongoClient.java#L802)
 * [connection pool maintenance](https://github.com/mongodb/mongo-java-driver/blob/67c9f738ae44bc15befb822644e7266634c7dcf5/driver-core/src/main/com/mongodb/internal/connection/DefaultConnectionPool.java#L95).

The spans resulting from these maintenance operations will not have a parent span.
