/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.mongodb;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.time.Duration;
import java.util.Arrays;
import org.bson.Document;
import org.opentest4j.TestAbortedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import static brave.mongodb.ITMongoDBTracing.COLLECTION_NAME;
import static brave.mongodb.ITMongoDBTracing.DATABASE_NAME;
import static java.util.Collections.singletonList;
import static org.testcontainers.utility.DockerImageName.parse;

final class MongoDBContainer extends GenericContainer<MongoDBContainer> {
  static final Logger LOGGER = LoggerFactory.getLogger(MongoDBContainer.class);
  static final int MONGODB_PORT = 27017;

  MongoDBContainer() {
    // Use OpenZipkin's small test image, which is multi-arch and doesn't consume Docker Hub quota
    super(parse("ghcr.io/openzipkin/mongodb-alpine:4.0.5"));
    if ("true".equals(System.getProperty("docker.skip"))) {
      throw new TestAbortedException("${docker.skip} == true");
    }
    withExposedPorts(MONGODB_PORT);
    waitStrategy = Wait.forLogMessage(".*waiting for connections.*", 1);
    withStartupTimeout(Duration.ofSeconds(60));
    withLogConsumer(new Slf4jLogConsumer(LOGGER));
  }

  @Override public void start() {
    super.start();
    try (MongoClient mongoClient = MongoClients.create(mongoClientSettingsBuilder().build())) {
      MongoDatabase database = mongoClient.getDatabase(DATABASE_NAME);
      MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);
      Document document1 = new Document("id", 1);
      Document document2 = new Document("id", 2);
      collection.insertMany(Arrays.asList(document1, document2));
    }
  }

  MongoClientSettings.Builder mongoClientSettingsBuilder() {
    return MongoClientSettings.builder().applyToClusterSettings(b -> b.hosts(singletonList(
      new ServerAddress(host(), port()))));
  }

  /** Return an IP address to ensure remote IP checks work. */
  String host() {
    return "127.0.0.1";
  }

  int port() {
    return getMappedPort(MONGODB_PORT);
  }
}
