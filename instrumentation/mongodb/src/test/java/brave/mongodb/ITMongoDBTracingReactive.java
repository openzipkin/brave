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
import com.mongodb.event.CommandListener;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

// TODO: Add support for Reactive tracing. See the notes in RATIONALE.md.
@Ignore("makesChildOfCurrentSpan() fails because support for reactive MongoDB tracing is unimplemented")
public class ITMongoDBTracingReactive extends ITMongoDBTracingBase {
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
    database = mongoClient.getDatabase("testDatabase");

    spans.clear();
  }

  @After public void close() {
    Tracing.current().close();
    if (mongoClient != null) mongoClient.close();
  }

  @Test public void makesChildOfCurrentSpan() throws InterruptedException {
    ScopedSpan parent = tracing.tracer().startScopedSpan("test");
    try {
      blockUntilComplete(database.getCollection(COLLECTION_NAME).find().first());
    } finally {
      parent.finish();
    }

    assertThat(spans)
      .hasSize(2);
  }

  static void blockUntilComplete(Publisher<?> publisher) throws InterruptedException {
    CountDownLatch countDownLatch = new CountDownLatch(1);

    publisher.subscribe(new Subscriber<Object>() {
      @Override public void onSubscribe(Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
      }

      @Override public void onNext(Object o) {

      }

      @Override public void onError(Throwable throwable) {

      }

      @Override
      public void onComplete() {
        countDownLatch.countDown();
      }
    });
    countDownLatch.await();
  }
}
