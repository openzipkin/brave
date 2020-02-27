/*
 * Copyright 2013-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.mongodb;

import brave.Span;
import brave.internal.Nullable;
import brave.propagation.ThreadLocalSpan;
import com.mongodb.MongoException;
import com.mongodb.MongoSocketException;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ConnectionId;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.json.JsonWriterSettings;

import java.net.InetSocketAddress;
import java.util.Set;

/**
 * A MongoDB command listener that will report via Brave how long each command takes and other information about the
 * commands.
 *
 * Implementation notes regarding the <b>synchronous</b> MongoDB clients ({@link com.mongodb.MongoClient} and
 * {@link com.mongodb.client.MongoClient}):
 * <p>It is sufficient to use {@link ThreadLocalSpan} because every command starts and ends on the same thread.</p>
 * <p>Most commands are executed in the thread where the {@code MongoClient} methods are called from, so (assuming that
 * the tracing context is correctly propagated to that thread) all spans should have the correct parent.</p>
 * <p>There are two exceptions to the above rule. Some maintenance operations are done on background threads:
 * <a href="https://github.com/mongodb/mongo-java-driver/blob/67c9f738ae44bc15befb822644e7266634c7dcf5/driver-legacy/src/main/com/mongodb/MongoClient.java#L802">cursor cleaning</a>
 * and
 * <a href="https://github.com/mongodb/mongo-java-driver/blob/67c9f738ae44bc15befb822644e7266634c7dcf5/driver-core/src/main/com/mongodb/internal/connection/DefaultConnectionPool.java#L95">connection pool maintenance</a>.
 * The spans resulting from these maintenance operations will not have a parent span.</p>
 *
 * Implementation notes regarding the <b>asynchronous</b> MongoDB clients ({@code com.mongodb.async.MongoClient} and
 * {@code com.mongodb.reactivestreams.client.MongoClient}:
 * <p>Support for asynchronous clients is <b>unimplemented</b>.</p>
 * <p>The asynchronous clients use threads for the async completion handlers (meaning that
 * {@link #commandStarted(CommandStartedEvent)}} and {@link #commandSucceeded(CommandSucceededEvent)}/
 * {@link #commandFailed(CommandFailedEvent)}} may get called from background threads and also not necessarily from the
 * same thread).</p>
 * <p>It should be possible to set a custom {@link com.mongodb.connection.StreamFactoryFactory} on the {@link
 * com.mongodb.MongoClientSettings.Builder} which can propagate the tracing context correctly between those handlers,
 * but this is <b>unimplemented</b> and it is unknown if this would be sufficient.</p>
 */
final class TraceMongoCommandListener implements CommandListener {
  final Set<String> commandsWithCollectionName;
  final int maxAbbreviatedCommandLength;
  final ThreadLocalSpan threadLocalSpan;
  final JsonWriterSettings jsonWriterSettings;

  TraceMongoCommandListener(MongoDBTracing mongoDBTracing) {
    this(mongoDBTracing, ThreadLocalSpan.create(mongoDBTracing.tracing.tracer()));
  }

  TraceMongoCommandListener(MongoDBTracing mongoDBTracing, ThreadLocalSpan threadLocalSpan) {
    commandsWithCollectionName = mongoDBTracing.commandsWithCollectionName;
    maxAbbreviatedCommandLength = mongoDBTracing.maxAbbreviatedCommandLength;
    this.threadLocalSpan = threadLocalSpan;
    jsonWriterSettings = JsonWriterSettings.builder()
      .maxLength(maxAbbreviatedCommandLength)
      .build();
  }

  /**
   * Uses {@link ThreadLocalSpan} as there's no attribute namespace shared between callbacks, but
   * all callbacks happen on the same thread.
   */
  @Override public void commandStarted(CommandStartedEvent event) {
    Span span = threadLocalSpan.next();
    if (span == null || span.isNoop()) return;

    String commandName = event.getCommandName();
    String databaseName = event.getDatabaseName();
    BsonDocument command = event.getCommand();
    String collectionName = getCollectionName(command, commandName);

    span.name(getSpanName(commandName, collectionName))
      .kind(Span.Kind.CLIENT)
      .remoteServiceName("mongodb-" + databaseName)
      .tag("mongodb.command.name", commandName);

    String abbreviatedCommand = getAbbreviatedCommand(command);
    if (abbreviatedCommand != null) {
      span.tag("mongodb.command", abbreviatedCommand);
    }

    if (collectionName != null) {
      span.tag("mongodb.collection", collectionName);
    }

    ConnectionDescription connectionDescription = event.getConnectionDescription();
    if (connectionDescription != null) {
      ConnectionId connectionId = connectionDescription.getConnectionId();
      if (connectionId != null) {
        span.tag("mongodb.cluster.id", connectionId.getServerId().getClusterId().getValue());
      }

      try {
        InetSocketAddress socketAddress = connectionDescription.getServerAddress().getSocketAddress();
        span.remoteIpAndPort(socketAddress.getAddress().getHostAddress(), socketAddress.getPort());
      } catch (MongoSocketException ignored) {

      }
    }

    span.start();
  }

  @Override public void commandSucceeded(CommandSucceededEvent event) {
    Span span = threadLocalSpan.remove();
    if (span == null) return;
    span.finish();
  }

  @Override public void commandFailed(CommandFailedEvent event) {
    Span span = threadLocalSpan.remove();
    if (span == null) return;
    Throwable throwable = event.getThrowable();
    span.error(throwable == null ? new MongoException("Command failed but no throwable was reported") : throwable);
    span.finish();
  }

  @Nullable String getCollectionName(BsonDocument command, String commandName) {
    if (commandsWithCollectionName.contains(commandName)) {
      String collectionName = getNonEmptyBsonString(command.get(commandName));
      if (collectionName != null) {
        return collectionName;
      }
    }
    // Some other commands, like getMore, have a field like {"collection": collectionName}.
    return getNonEmptyBsonString(command.get("collection"));
  }

  /**
   * @return trimmed string from {@code bsonValue} or null if the trimmed string was empty or the value wasn't a string
   */
  @Nullable static String getNonEmptyBsonString(BsonValue bsonValue) {
    if (bsonValue == null || !bsonValue.isString()) return null;
    String stringValue = bsonValue.asString().getValue().trim();
    return stringValue.isEmpty() ? null : stringValue;
  }

  /**
   * Returns an abbreviated version of the command for logging/tracing purposes. Currently this is simply a
   * truncated version of the command's JSON string representation.
   *
   * Note that sensitive data related to the MongoDB protocol itself is already scrubbed at this point according to
   * https://github.com/mongodb/specifications/blob/master/source/command-monitoring/command-monitoring.rst#security
   *
   * @return an abbreviated version of the command for logging/tracing purposes
   */
  @Nullable String getAbbreviatedCommand(BsonDocument command) {
    if (maxAbbreviatedCommandLength <= 0) return null;
    String abbreviatedCommand = command.toJson(jsonWriterSettings);
    return abbreviatedCommand.isEmpty() ? null : abbreviatedCommand;
  }

  static String getSpanName(String commandName, @Nullable String collectionName) {
    return commandName + (collectionName == null ? "" : (" " + collectionName));
  }
}
