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

import brave.ScopedSpan;
import brave.Tracing;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoQueryException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.event.CommandListener;
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

public class ITMongoDBTracing extends ITMongoDBTracingBase {
  MongoClient mongoClient;
  MongoDatabase database;

  @Before public void init() {
    CommandListener listener = MongoDBTracing.newBuilder(tracing)
      .build()
      .commandListener();
    MongoClientSettings settings = mongoClientSettingsBuilder()
      .addCommandListener(listener)
      .build();
    mongoClient = MongoClients.create(settings);
    database = mongoClient.getDatabase(DATABASE_NAME);

    spans.clear();
  }

  @After public void close() {
    Tracing.current().close();
    if (mongoClient != null) mongoClient.close();
  }

  @Test public void makesChildOfCurrentSpan() {
    ScopedSpan parent = tracing.tracer().startScopedSpan("test");
    try {
      executeFind(COLLECTION_NAME);
    } finally {
      parent.finish();
    }

    assertThat(spans)
      .hasSize(2);
  }

  @Test public void reportsClientKind() {
    executeFind(COLLECTION_NAME);

    assertThat(spans)
      .extracting(Span::kind)
      .containsExactly(Span.Kind.CLIENT);
  }

  @Test
  public void defaultSpanNameIsCommandNameAndCollectionName() {
    MongoCursor<?> mongoCursor = database.getCollection(COLLECTION_NAME).find().batchSize(1).iterator();

    assertThat(mongoCursor.hasNext()).isTrue(); // id=1
    mongoCursor.next();
    assertThat(mongoCursor.hasNext()).isTrue(); // id=2
    mongoCursor.next();

    // getMore
    assertThat(spans)
      .extracting(Span::name)
      .containsExactly(
        "find " + COLLECTION_NAME.toLowerCase(), // extracted from {"find": "myCollection"}
        "getmore " + COLLECTION_NAME.toLowerCase() // extracted from {"getMore": <cursorId>, "collection": "myCollection"}
      );
  }

  /** This intercepts all commands, not just queries. This ensures commands without a collection name work */
  @Test
  public void defaultSpanNameIsCommandName_notStringArgument() {
    database.listCollections().first();

    assertThat(spans)
      .extracting(Span::name)
      .containsExactly("listcollections");
  }

  @Test
  public void defaultSpanNameIsCommandName_nonCollectionCommand() {
    try {
      database.runCommand(new BsonDocument("dropUser", new BsonString("testUser")));
    } catch (MongoCommandException ignored) {
      // Expected, we are trying to drop a user that doesn't exist
    }

    assertThat(spans)
      .extracting(Span::name)
      .containsExactly("dropuser"); // "testUser" should not be mistaken as a collection name
  }

  @Test
  public void addsTags() {
    executeFind(COLLECTION_NAME);

    assertThat(spans)
      .flatExtracting(s -> s.tags().entrySet())
      .contains(
        entry("mongodb.collection", COLLECTION_NAME),
        entry("mongodb.command", "find")
      )
      .anyMatch(entry -> "mongodb.cluster_id".equals(entry.getKey()) && !entry.getValue().isEmpty());
  }

  @Test
  public void addsTagsForLargePayloadCommand() {
    Document largeDocument = new Document();
    for (int i = 0; i < 500_000; ++i) {
      largeDocument.put("key" + i, "value" + i);
    }
    database.getCollection("largeCollection").insertOne(largeDocument);

    assertThat(spans)
      .flatExtracting(s -> s.tags().entrySet())
      .contains(
        entry("mongodb.collection", "largeCollection"),
        entry("mongodb.command", "insert")
      )
      .anyMatch(entry -> "mongodb.cluster_id".equals(entry.getKey()) && !entry.getValue().isEmpty());
  }

  @Test
  public void reportsServerAddress() {
    executeFind(COLLECTION_NAME);

    assertThat(spans)
      .extracting(Span::remoteServiceName)
      .contains("mongodb-" + DATABASE_NAME.toLowerCase());
  }

  @Test
  public void mongoError() {
    assertThatThrownBy(() -> executeFind(INVALID_COLLECTION_NAME)).isInstanceOf(MongoQueryException.class);
    assertThat(spans)
      .isNotEmpty();

    assertThat(spans)
      .anySatisfy(span -> assertThat(span.tags()).containsKey("error"));
  }

  void executeFind(String collectionName) {
    database.getCollection(collectionName).find().first();
  }
}
