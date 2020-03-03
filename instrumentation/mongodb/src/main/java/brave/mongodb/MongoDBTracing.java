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

import brave.Tracing;
import com.mongodb.event.CommandListener;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Use this class to decorate your MongoDB client and enable Tracing.
 *
 * <p>To use it, call <code>.addCommandListener(MongoDBTracing.create(tracing).commandListener())</code>
 * on the {@link com.mongodb.MongoClientOptions} or {@link com.mongodb.MongoClientSettings} object that is used to
 * create the {@code MongoClient} to be instrumented.
 *
 * As of now, this instrumentation can only be used with the synchronous MongoDB driver. Do not use it with the
 * asynchronous or reactive drivers as tracing data will be incorrect.
 */
public final class MongoDBTracing {
  public static MongoDBTracing create(final Tracing tracing) {
    return newBuilder(tracing).build();
  }

  public static Builder newBuilder(final Tracing tracing) {
    return new Builder(tracing);
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  public CommandListener commandListener() {
    return new TraceMongoCommandListener(this);
  }

  public static final class Builder {
    final Tracing tracing;
    // See https://docs.mongodb.com/manual/reference/command for the command reference
    static final List<String> DEFAULT_COMMANDS_WITH_COLLECTION_NAME = Arrays.asList("aggregate", "count", "distinct",
      "mapReduce", "geoSearch", "delete", "find", "findAndModify", "insert", "update", "collMod", "compact",
      "convertToCapped", "create", "createIndexes", "drop", "dropIndexes", "killCursors", "listIndexes", "reIndex");
    final Set<String> commandsWithCollectionName = new HashSet<>();

    Builder(Tracing tracing) {
      if (tracing == null) throw new NullPointerException("tracing == null");
      this.tracing = tracing;
      commandsWithCollectionName.addAll(DEFAULT_COMMANDS_WITH_COLLECTION_NAME);
    }

    Builder(MongoDBTracing mongoDBTracing) {
      tracing = mongoDBTracing.tracing;
      commandsWithCollectionName.addAll(mongoDBTracing.commandsWithCollectionName);
    }

    /**
     * Clear the allow-list of command names for which tracing will attempt to extract the collection/view name from
     * the argument.
     */
    public Builder clearCommandsWithCollectionName() {
      commandsWithCollectionName.clear();
      return this;
    }

    /**
     * Adds a MongoDB command name to the allow-list, indicating that if the command's argument is a string, the
     * argument contains the collection/view name. Tracing will only attempt to extract the collection/view name
     * argument from allow-listed commands.
     *
     * The default allow-list is a set of commonly used commands ({@link #DEFAULT_COMMANDS_WITH_COLLECTION_NAME}) that
     * operate on collections/views.
     *
     * To replace the list completely, don't forget to call {@link #clearCommandsWithCollectionName()} first.
     *
     * @param commandName command name to add to the allow-list
     */
    public Builder addCommandWithCollectionName(String commandName) {
      commandsWithCollectionName.add(commandName);
      return this;
    }

    /**
     * Same as {@link #addCommandWithCollectionName(String)} but for a collection of command names.
     */
    public Builder addAllCommandsWithCollectionName(Collection<String> commandNames) {
      commandsWithCollectionName.addAll(commandNames);
      return this;
    }

    public MongoDBTracing build() {
      return new MongoDBTracing(this);
    }
  }

  final Tracing tracing;
  final Set<String> commandsWithCollectionName;

  MongoDBTracing(Builder builder) {
    tracing = builder.tracing;
    commandsWithCollectionName = Collections.unmodifiableSet(new HashSet<>(builder.commandsWithCollectionName));
  }
}
