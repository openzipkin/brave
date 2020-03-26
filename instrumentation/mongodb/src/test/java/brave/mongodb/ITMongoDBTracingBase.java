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
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.testcontainers.containers.GenericContainer;
import zipkin2.Span;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ITMongoDBTracingBase {
  static String DATABASE_NAME = "myDatabase";
  static String COLLECTION_NAME = "myCollection";
  static String INVALID_COLLECTION_NAME = "?.$";

  static int MONGODB_PORT = 27017;

  BlockingQueue<Span> spans = new LinkedBlockingQueue<>();
  Tracing tracing = tracingBuilder(Sampler.ALWAYS_SAMPLE).build();

  @ClassRule
  public static GenericContainer<?> mongo = new GenericContainer<>("mongo:4.0")
    .withExposedPorts(MONGODB_PORT);

  @BeforeClass public static void initClass() {
    MongoClient mongoClient = MongoClients.create(mongoClientSettingsBuilder().build());
    MongoDatabase database = mongoClient.getDatabase(DATABASE_NAME);
    MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);
    Document document1 = new Document("id", 1);
    Document document2 = new Document("id", 2);
    collection.insertMany(Arrays.asList(document1, document2));
  }

  Tracing.Builder tracingBuilder(Sampler sampler) {
    return Tracing.newBuilder()
      .spanReporter(spans::add)
      .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
        .addScopeDecorator(StrictScopeDecorator.create())
        .build())
      .sampler(sampler);
  }

  static MongoClientSettings.Builder mongoClientSettingsBuilder() {
    return MongoClientSettings.builder()
      .applyToClusterSettings(builder ->
        builder.hosts(Collections.singletonList(new ServerAddress(mongo.getContainerIpAddress(), mongo.getMappedPort(MONGODB_PORT)))));
  }
}
