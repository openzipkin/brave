/*
 * Copyright 2013-2024 The OpenZipkin Authors
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

import brave.handler.MutableSpan;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.test.ITRemote;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static brave.Span.Kind.CLIENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

@Tag("docker")
@Testcontainers(disabledWithoutDocker = true)
@Timeout(60)
public class ITMongoDBTracing extends ITRemote { // public for invoker test
  static final String DATABASE_NAME = "myDatabase";
  static final String COLLECTION_NAME = "myCollection";
  static final String INVALID_COLLECTION_NAME = "?.$";

  @Container MongoDBContainer mongo = new MongoDBContainer();
  CommandListener listener = MongoDBTracing.newBuilder(tracing).build().commandListener();
  MongoClientSettings settings;
  MongoClient mongoClient;
  MongoDatabase database;
  String clusterId;

  @BeforeEach void initClient() {
    settings = mongo.mongoClientSettingsBuilder().addCommandListener(listener).build();
    mongoClient = MongoClients.create(settings);
    database = mongoClient.getDatabase(DATABASE_NAME);
  }

  @AfterEach void closeClient() {
    mongoClient.close();
  }

  @BeforeEach void getClusterId() throws Exception {
    // TODO: Figure out an easier way to get this!
    Field clusterIdField =
      Class.forName("com.mongodb.internal.connection.BaseCluster").getDeclaredField("clusterId");

    clusterIdField.setAccessible(true);
    ClusterId clusterId =
      (ClusterId) clusterIdField.get(((MongoClientImpl) mongoClient).getCluster());
    this.clusterId = clusterId.getValue();
  }

  @Test void makesChildOfCurrentSpan() {
    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      executeFind(COLLECTION_NAME);
    }

    MutableSpan clientSpan = testSpanHandler.takeRemoteSpan(CLIENT);
    assertChildOf(clientSpan, parent);
  }

  @Test void reportsClientKind() {
    executeFind(COLLECTION_NAME);

    testSpanHandler.takeRemoteSpan(CLIENT);
  }

  @Test void defaultSpanNameIsCommandNameAndCollectionName() {
    MongoCursor<?> mongoCursor =
      database.getCollection(COLLECTION_NAME).find().batchSize(1).iterator();

    assertThat(mongoCursor.hasNext()).isTrue(); // id=1
    mongoCursor.next();
    assertThat(mongoCursor.hasNext()).isTrue(); // id=2
    mongoCursor.next();

    // Name extracted from {"find": "myCollection"}
    assertThat(testSpanHandler.takeRemoteSpan(CLIENT).name()).isEqualTo("find " + COLLECTION_NAME);

    // Name extracted from {"getMore": <cursorId>, "collection": "myCollection"}
    assertThat(testSpanHandler.takeRemoteSpan(CLIENT).name()).isEqualTo(
      "getMore " + COLLECTION_NAME);
  }

  /**
   * This intercepts all commands, not just queries. This ensures commands without a collection name
   * work
   */
  @Test void defaultSpanNameIsCommandName_notStringArgument() {
    database.listCollections().first();

    assertThat(testSpanHandler.takeRemoteSpan(CLIENT).name()).isEqualTo("listCollections");
  }

  @Test void defaultSpanNameIsCommandName_nonCollectionCommand() {
    try {
      database.runCommand(new BsonDocument("dropUser", new BsonString("testUser")));

      // Expected, we are trying to drop a user that doesn't exist
      failBecauseExceptionWasNotThrown(MongoCommandException.class);
    } catch (MongoCommandException e) {
      MutableSpan span = testSpanHandler.takeRemoteSpanWithError(CLIENT, e);

      // "testUser" should not be mistaken as a collection name
      assertThat(span.name()).isEqualTo("dropUser");
    }
  }

  @Test void addsTags() {
    executeFind(COLLECTION_NAME);

    assertThat(testSpanHandler.takeRemoteSpan(CLIENT).tags()).containsOnly(
      entry("mongodb.collection", COLLECTION_NAME), entry("mongodb.command", "find"),
      entry("mongodb.cluster_id", clusterId));
  }

  @Test void addsTagsForLargePayloadCommand() {
    Document largeDocument = new Document();
    for (int i = 0; i < 500_000; ++i) {
      largeDocument.put("key" + i, "value" + i);
    }
    database.getCollection("largeCollection").insertOne(largeDocument);

    assertThat(testSpanHandler.takeRemoteSpan(CLIENT).tags()).containsOnly(
      entry("mongodb.collection", "largeCollection"), entry("mongodb.command", "insert"),
      entry("mongodb.cluster_id", clusterId));
  }

  @Test void reportsServerAddress() {
    executeFind(COLLECTION_NAME);

    assertThat(testSpanHandler.takeRemoteSpan(CLIENT).remoteServiceName()).isEqualTo(
      "mongodb-" + DATABASE_NAME);
  }

  @Test void setsError() {
    assertThatThrownBy(() -> executeFind(INVALID_COLLECTION_NAME)).isInstanceOf(
      MongoQueryException.class);

    // the error the interceptor receives is a MongoCommandException!
    testSpanHandler.takeRemoteSpanWithErrorMessage(CLIENT, ".*InvalidNamespace.*");
  }

  void executeFind(String collectionName) {
    database.getCollection(collectionName).find().first();
  }
}
