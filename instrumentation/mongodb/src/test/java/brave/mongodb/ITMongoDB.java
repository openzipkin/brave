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

import brave.test.ITRemote;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.util.Arrays;
import org.bson.Document;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static java.util.Collections.singletonList;

public abstract class ITMongoDB extends ITRemote { // public because of ClassRule
  static final String DATABASE_NAME = "myDatabase";
  static final String COLLECTION_NAME = "myCollection";
  static final String INVALID_COLLECTION_NAME = "?.$";
  static final int MONGODB_PORT = 27017;

  @ClassRule
  public static GenericContainer<?> mongo = new GenericContainer<>(
    // Use OpenZipkin's small test image, which is multi-arch and doesn't consume Docker Hub quota
    DockerImageName.parse("ghcr.io/openzipkin/mongodb-alpine:4.0.5"))
    .withExposedPorts(MONGODB_PORT);

  @BeforeClass public static void initCollection() {
    try (MongoClient mongoClient = MongoClients.create(mongoClientSettingsBuilder().build())) {
      MongoDatabase database = mongoClient.getDatabase(DATABASE_NAME);
      MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);
      Document document1 = new Document("id", 1);
      Document document2 = new Document("id", 2);
      collection.insertMany(Arrays.asList(document1, document2));
    }
  }

  static MongoClientSettings.Builder mongoClientSettingsBuilder() {
    return MongoClientSettings.builder().applyToClusterSettings(b -> b.hosts(singletonList(
      new ServerAddress(mongo.getContainerIpAddress(), mongo.getMappedPort(MONGODB_PORT)))));
  }
}
