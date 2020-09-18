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
import com.mongodb.MongoSocketException;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ConnectionId;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.bson.BsonDocument;
import org.bson.BsonValue;

import static brave.Span.Kind.CLIENT;

/**
 * A MongoDB command listener that will report via Brave how long each command takes and other
 * information about the commands.
 *
 * See <a href="https://github.com/openzipkin/brave/blob/master/instrumentation/mongodb/RATIONALE.md">RATIONALE.md</a>
 * for implementation notes.
 */
final class TraceMongoCommandListener implements CommandListener {
  // See https://docs.mongodb.com/manual/reference/command for the command reference
  static final Set<String> COMMANDS_WITH_COLLECTION_NAME = new LinkedHashSet<>(Arrays.asList(
    "aggregate", "count", "distinct", "mapReduce", "geoSearch", "delete", "find", "findAndModify",
    "insert", "update", "collMod", "compact", "convertToCapped", "create", "createIndexes", "drop",
    "dropIndexes", "killCursors", "listIndexes", "reIndex"));

  final ThreadLocalSpan threadLocalSpan;

  TraceMongoCommandListener(MongoDBTracing mongoDBTracing) {
    this(ThreadLocalSpan.create(mongoDBTracing.tracing.tracer()));
  }

  TraceMongoCommandListener(ThreadLocalSpan threadLocalSpan) {
    this.threadLocalSpan = threadLocalSpan;
  }

  /**
   * Uses {@link ThreadLocalSpan} as there's no attribute namespace shared between callbacks, but
   * all callbacks happen on the same thread.
   */
  @Override public void commandStarted(CommandStartedEvent event) {
    String databaseName = event.getDatabaseName();
    if ("admin".equals(databaseName)) return; // don't trace commands like "endSessions"

    Span span = threadLocalSpan.next();
    if (span == null || span.isNoop()) return;

    String commandName = event.getCommandName();
    BsonDocument command = event.getCommand();
    String collectionName = getCollectionName(command, commandName);

    span.name(getSpanName(commandName, collectionName))
      .kind(CLIENT)
      .remoteServiceName("mongodb-" + databaseName)
      .tag("mongodb.command", commandName);

    if (collectionName != null) {
      span.tag("mongodb.collection", collectionName);
    }

    ConnectionDescription connectionDescription = event.getConnectionDescription();
    if (connectionDescription != null) {
      ConnectionId connectionId = connectionDescription.getConnectionId();
      if (connectionId != null) {
        span.tag("mongodb.cluster_id", connectionId.getServerId().getClusterId().getValue());
      }

      try {
        InetSocketAddress socketAddress =
          connectionDescription.getServerAddress().getSocketAddress();
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
    span.error(event.getThrowable());
    span.finish();
  }

  @Nullable String getCollectionName(BsonDocument command, String commandName) {
    if (COMMANDS_WITH_COLLECTION_NAME.contains(commandName)) {
      String collectionName = getNonEmptyBsonString(command.get(commandName));
      if (collectionName != null) {
        return collectionName;
      }
    }
    // Some other commands, like getMore, have a field like {"collection": collectionName}.
    return getNonEmptyBsonString(command.get("collection"));
  }

  /**
   * @return trimmed string from {@code bsonValue} or null if the trimmed string was empty or the
   * value wasn't a string
   */
  @Nullable static String getNonEmptyBsonString(BsonValue bsonValue) {
    if (bsonValue == null || !bsonValue.isString()) return null;
    String stringValue = bsonValue.asString().getValue().trim();
    return stringValue.isEmpty() ? null : stringValue;
  }

  static String getSpanName(String commandName, @Nullable String collectionName) {
    if (collectionName == null) return commandName;
    return commandName + " " + collectionName;
  }
}
