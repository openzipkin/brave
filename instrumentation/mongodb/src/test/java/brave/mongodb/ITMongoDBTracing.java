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

import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoQueryException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.internal.MongoClientImpl;
import com.mongodb.connection.ClusterId;
import com.mongodb.event.CommandListener;
import java.lang.reflect.Field;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

public class ITMongoDBTracing extends ITMongoDB {
  CommandListener listener = MongoDBTracing.newBuilder(tracing).build().commandListener();
  MongoClientSettings settings = mongoClientSettingsBuilder().addCommandListener(listener).build();
  MongoClient mongoClient = MongoClients.create(settings);
  MongoDatabase database = mongoClient.getDatabase(DATABASE_NAME);
  String clusterId;

  @Before public void getClusterId() throws Exception {
    // TODO: Figure out an easier way to get this!
    Field clusterIdField = Class.forName("com.mongodb.internal.connection.BaseCluster")
      .getDeclaredField("clusterId");

    clusterIdField.setAccessible(true);
    ClusterId clusterId =
      (ClusterId) clusterIdField.get(((MongoClientImpl) mongoClient).getCluster());
    this.clusterId = clusterId.getValue();
  }

  @After public void closeClient() {
    mongoClient.close();
  }

  @Test public void makesChildOfCurrentSpan() {
    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      executeFind(COLLECTION_NAME);
    }

    Span clientSpan = reporter.takeRemoteSpan(Span.Kind.CLIENT);
    assertChildOf(clientSpan, parent);
  }

  @Test public void reportsClientKind() {
    executeFind(COLLECTION_NAME);

    reporter.takeRemoteSpan(Span.Kind.CLIENT);
  }

  @Test public void defaultSpanNameIsCommandNameAndCollectionName() {
    MongoCursor<?> mongoCursor =
      database.getCollection(COLLECTION_NAME).find().batchSize(1).iterator();

    assertThat(mongoCursor.hasNext()).isTrue(); // id=1
    mongoCursor.next();
    assertThat(mongoCursor.hasNext()).isTrue(); // id=2
    mongoCursor.next();

    // Name extracted from {"find": "myCollection"}
    assertThat(reporter.takeRemoteSpan(Span.Kind.CLIENT).name())
      .isEqualTo("find " + COLLECTION_NAME.toLowerCase());

    // Name extracted from {"getMore": <cursorId>, "collection": "myCollection"}
    assertThat(reporter.takeRemoteSpan(Span.Kind.CLIENT).name())
      .isEqualTo("getmore " + COLLECTION_NAME.toLowerCase());
  }

  /**
   * This intercepts all commands, not just queries. This ensures commands without a collection name
   * work
   */
  @Test public void defaultSpanNameIsCommandName_notStringArgument() {
    database.listCollections().first();

    assertThat(reporter.takeRemoteSpan(Span.Kind.CLIENT).name())
      .isEqualTo("listcollections");
  }

  @Test public void defaultSpanNameIsCommandName_nonCollectionCommand() {
    // Expected, we are trying to drop a user that doesn't exist
    assertThatThrownBy(() ->
      database.runCommand(new BsonDocument("dropUser", new BsonString("testUser")))
    ).isInstanceOf(MongoCommandException.class);

    Span span = reporter.takeRemoteSpanWithError(Span.Kind.CLIENT, ".*UserNotFound.*");

    // "testUser" should not be mistaken as a collection name
    assertThat(span.name())
      .isEqualTo("dropuser");
  }

  @Test public void addsTags() {
    executeFind(COLLECTION_NAME);

    assertThat(reporter.takeRemoteSpan(Span.Kind.CLIENT).tags()).containsOnly(
      entry("mongodb.collection", COLLECTION_NAME),
      entry("mongodb.command", "find"),
      entry("mongodb.cluster_id", clusterId)
    );
  }

  @Test public void addsTagsForLargePayloadCommand() {
    Document largeDocument = new Document();
    for (int i = 0; i < 500_000; ++i) {
      largeDocument.put("key" + i, "value" + i);
    }
    database.getCollection("largeCollection").insertOne(largeDocument);

    assertThat(reporter.takeRemoteSpan(Span.Kind.CLIENT).tags()).containsOnly(
      entry("mongodb.collection", "largeCollection"),
      entry("mongodb.command", "insert"),
      entry("mongodb.cluster_id", clusterId)
    );
  }

  @Test public void reportsServerAddress() {
    executeFind(COLLECTION_NAME);

    assertThat(reporter.takeRemoteSpan(Span.Kind.CLIENT).remoteServiceName())
      .isEqualTo("mongodb-" + DATABASE_NAME.toLowerCase());
  }

  @Test public void mongoError() {
    assertThatThrownBy(() -> executeFind(INVALID_COLLECTION_NAME))
      .isInstanceOf(MongoQueryException.class);

    reporter.takeRemoteSpanWithError(Span.Kind.CLIENT, ".*InvalidNamespace.*");
  }

  void executeFind(String collectionName) {
    database.getCollection(collectionName).find().first();
  }
}
