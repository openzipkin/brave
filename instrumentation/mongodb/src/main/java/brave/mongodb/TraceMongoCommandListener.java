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
import brave.Tracer;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.json.JsonWriterSettings;

import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Mongo command listener that will report via Brave how long each command takes.
 *
 * <p>To use it, call <code>.addCommandListener(TraceMongoCommandListener.builder().tracer(tracer).build())</code>
 * on the {@link com.mongodb.MongoClientOptions} or {@link com.mongodb.MongoClientSettings} object that is used to
 * create the {@code MongoClient} to be instrumented.
 */
public class TraceMongoCommandListener implements CommandListener {
  private final Tracer tracer;
  private final int maxAbbreviatedCommandLength;
  private final JsonWriterSettings jsonWriterSettings;
  private final Map<Integer, Span> activeSpans = new ConcurrentHashMap<>();

  public static Builder builder() {
    return new Builder();
  }

  protected TraceMongoCommandListener(final Builder builder) {
    tracer = Objects.requireNonNull(builder.tracer, "tracer cannot be null");
    maxAbbreviatedCommandLength = builder.maxAbbreviatedCommandLength;
    jsonWriterSettings = JsonWriterSettings.builder()
      .maxLength(Math.max(0, builder.maxAbbreviatedCommandLength))
      .build();
  }

  @Override public void commandStarted(final CommandStartedEvent event) {
    final Span span = tracer.nextSpan();
    if (span.isNoop()) {
      return;
    }
    activeSpans.put(event.getRequestId(), span);

    final String commandName = event.getCommandName();
    final String databaseName = event.getDatabaseName();
    final BsonDocument command = event.getCommand();
    final Optional<String> collectionName = getCollectionName(command);

    span.name(getSpanName(commandName, collectionName))
      .kind(Span.Kind.CLIENT)
      .remoteServiceName(databaseName)
      .tag("db.type", "mongo")
      .tag("mongo.database", databaseName)
      .tag("mongo.operation", commandName);

    getAbbreviatedCommand(command).ifPresent(abbreviatedCommand -> span.tag("mongo.command", abbreviatedCommand));

    collectionName.ifPresent(collection -> span.tag("mongo.collection", collection));

    final InetSocketAddress socketAddress = event.getConnectionDescription().getServerAddress().getSocketAddress();
    span.remoteIpAndPort(socketAddress.getAddress().getHostAddress(), socketAddress.getPort());

    span.start();
  }

  @Override public void commandSucceeded(final CommandSucceededEvent event) {
    final Span span = activeSpans.remove(event.getRequestId());
    if (span == null) {
      return;
    }
    span.finish();
  }

  @Override public void commandFailed(final CommandFailedEvent event) {
    final Span span = activeSpans.remove(event.getRequestId());
    if (span == null) {
      return;
    }
    span.error(event.getThrowable());
    span.finish();
  }

  static Optional<String> getCollectionName(final BsonDocument command) {
    final Iterator<Map.Entry<String, BsonValue>> iterator = command.entrySet().iterator();
    if (!iterator.hasNext()) {
      return Optional.empty();
    }
    final BsonValue value = iterator.next().getValue();
    if (!value.isString()) {
      return Optional.empty();
    }
    final String stringValue = value.asString().getValue().trim();
    return stringValue.isEmpty() ? Optional.empty() : Optional.of(stringValue);
  }

  /**
   * Returns an abbreviated version of the command for logging/tracing purposes. Currently this is simply a
   * truncated version of the command's JSON string representation.
   *
   * Note that sensitive data related to the Mongo protocol itself is already scrubbed at this point according to
   * https://github.com/mongodb/specifications/blob/master/source/command-monitoring/command-monitoring.rst#security
   *
   * @return an abbreviated version of the command for logging/tracing purposes
   */
  Optional<String> getAbbreviatedCommand(final BsonDocument command) {
    if (maxAbbreviatedCommandLength <= 0) {
      return Optional.empty();
    }
    final String abbreviatedCommand = command.toJson(jsonWriterSettings);
    return abbreviatedCommand.isEmpty() ? Optional.empty() : Optional.of(abbreviatedCommand);
  }

  static String getSpanName(final String commandName, final Optional<String> collectionName) {
    return collectionName.map(collection -> commandName + " " + collection).orElse(commandName);
  }

  public static class Builder {
    private Tracer tracer;
    private int maxAbbreviatedCommandLength = 100;

    /**
     * Sets the {@link Tracer} to use. Required.
     */
    public Builder tracer(final Tracer tracer) {
      this.tracer = tracer;
      return this;
    }

    /**
     * Sets how many characters of the Mongo command to report in the "mongo.command" tag. Defaults to 100.
     * If non-positive, command reporting will be disabled.
     */
    public Builder maxAbbreviatedCommandLength(final int maxAbbreviatedCommandLength) {
      this.maxAbbreviatedCommandLength = maxAbbreviatedCommandLength;
      return this;
    }

    public TraceMongoCommandListener build() {
      return new TraceMongoCommandListener(this);
    }
  }
}
