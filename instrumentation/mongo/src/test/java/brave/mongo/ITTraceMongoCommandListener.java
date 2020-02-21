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
package brave.mongo;

import brave.ScopedSpan;
import brave.Tracing;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoQueryException;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.event.CommandListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Span;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.Assume.assumeTrue;

public class ITTraceMongoCommandListener {
  private static final String A_COLLECTION_NAME = "myCollection"; // collection doesn't have to exist
  private static final String INVALID_COLLECTION_NAME = "?.$";

  private final List<Span> spans = new CopyOnWriteArrayList<>();

  private final Tracing tracing = tracingBuilder(Sampler.ALWAYS_SAMPLE).build();
  private MongoClient mongoClient;
  private MongoDatabase database;

  @Before public void init() {
    final String databaseName = System.getenv("MONGODB_DB");
    assumeTrue("Minimally, the environment variable MONGODB_DB must be set",
      databaseName != null);

    final CommandListener listener = TraceMongoCommandListener.builder()
      .maxAbbreviatedCommandLength(8)
      .tracer(Tracing.currentTracer())
      .build();
    final MongoClientSettings settings = MongoClientSettings.builder()
      .applyToClusterSettings(builder ->
        builder.hosts(Collections.singletonList(new ServerAddress(envOr("MONGODB_HOST", "127.0.0.1"), envOr("MONGODB_PORT", 27017)))))
      .addCommandListener(listener)
      .build();
    mongoClient = MongoClients.create(settings);
    database = mongoClient.getDatabase(databaseName);

    spans.clear();
  }

  @After public void close() {
    Tracing.current().close();
    if (mongoClient != null) mongoClient.close();
  }

  @Test public void makesChildOfCurrentSpan() {
    ScopedSpan parent = tracing.tracer().startScopedSpan("test");
    try {
      executeFind(A_COLLECTION_NAME);
    } finally {
      parent.finish();
    }

    assertThat(spans)
      .hasSize(2);
  }

  @Test public void reportsClientKind() {
    executeFind(A_COLLECTION_NAME);

    assertThat(spans)
      .extracting(Span::kind)
      .containsExactly(Span.Kind.CLIENT);
  }

  @Test
  public void defaultSpanNameIsOperationAndCollectionName() {
    executeFind(A_COLLECTION_NAME);

    assertThat(spans)
      .extracting(Span::name)
      .containsExactly("find " + A_COLLECTION_NAME.toLowerCase());
  }

  /** This intercepts all commands, not just queries. This ensures commands without a collection name work */
  @Test
  public void defaultSpanNameIsOperationName_oneWord() {
    database.listCollections().first();

    assertThat(spans)
      .extracting(Span::name)
      .contains("listcollections");
  }

  @Test
  public void addsTags() {
    executeFind(A_COLLECTION_NAME);

    assertThat(spans)
      .flatExtracting(s -> s.tags().entrySet())
      .containsExactlyInAnyOrder(
        entry("db.type", "mongo"),
        entry("mongo.database", database.getName()),
        entry("mongo.collection", A_COLLECTION_NAME),
        entry("mongo.operation", "find"),
        entry("mongo.command", "{\"find\":")
      );
  }

  @Test
  public void reportsServerAddress() {
    executeFind(A_COLLECTION_NAME);

    assertThat(spans)
      .extracting(Span::remoteServiceName)
      .contains(database.getName());
  }

  @Test
  public void mongoError() {
    assertThatThrownBy(() -> executeFind(INVALID_COLLECTION_NAME)).isInstanceOf(MongoQueryException.class);
    assertThat(spans)
      .isNotEmpty();

    assertThat(spans)
      .anySatisfy(span -> assertThat(span.tags()).containsKey("error"));
  }

  private void executeFind(final String collectionName) {
    database.getCollection(collectionName).find().first();
  }

  private Tracing.Builder tracingBuilder(final Sampler sampler) {
    return Tracing.newBuilder()
      .spanReporter(spans::add)
      .currentTraceContext(ThreadLocalCurrentTraceContext.create())
      .sampler(sampler);
  }

  private static int envOr(final String key, final int fallback) {
    return System.getenv(key) != null ? Integer.parseInt(System.getenv(key)) : fallback;
  }

  private static String envOr(final String key, final String fallback) {
    return System.getenv(key) != null ? System.getenv(key) : fallback;
  }
}
