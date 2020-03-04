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
import brave.internal.Nullable;
import brave.propagation.ThreadLocalSpan;
import com.mongodb.MongoException;
import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ServerId;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonElement;
import org.bson.BsonString;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;

import static brave.mongodb.TraceMongoCommandListener.getNonEmptyBsonString;
import static brave.mongodb.TraceMongoCommandListener.getSpanName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TraceMongoCommandListenerTest {
  static BsonDocument LONG_COMMAND = BsonDocument.parse("{" +
    "   \"insert\": \"myCollection\",\n" +
    "   \"bar\": {" +
    "       \"test\": \"asdfghjkl\",\n" +
    "   },\n" +
    "}");

  static Throwable EXCEPTION = new RuntimeException("Error occurred");

  @Mock ThreadLocalSpan threadLocalSpan;
  @Mock Span span;

  TraceMongoCommandListener listener;

  @Before public void setUp() {
    listener = new TraceMongoCommandListener(threadLocalSpan);
  }

  @Test public void getCollectionName_missingCommand() {
    assertThat(listener.getCollectionName(new BsonDocument(), "find")).isNull();
  }

  @Test public void getCollectionName_notStringCommandArgument() {
    assertThat(listener.getCollectionName(new BsonDocument("find", BsonBoolean.TRUE), "find")).isNull();
  }

  @Test public void getCollectionName_emptyStringCommandArgument() {
    assertThat(listener.getCollectionName(new BsonDocument("find", new BsonString("  ")), "find")).isNull();
  }

  @Test public void getCollectionName_notAllowListedCommand() {
    assertThat(listener.getCollectionName(new BsonDocument("cmd", new BsonString(" bar ")), "cmd")).isNull();
  }

  @Test public void getCollectionName_allowListedCommand() {
    assertThat(listener.getCollectionName(new BsonDocument("find", new BsonString(" bar ")), "find")).isEqualTo("bar");
  }

  @Test public void getCollectionName_collectionFieldOnly() {
    assertThat(listener.getCollectionName(new BsonDocument("collection", new BsonString(" bar ")), "find")).isEqualTo("bar");
  }

  @Test public void getCollectionName_allowListedCommandAndCollectionField() {
    BsonDocument command = new BsonDocument(Arrays.asList(
      new BsonElement("collection", new BsonString("coll")),
      new BsonElement("find", new BsonString("bar"))
    ));
    assertThat(listener.getCollectionName(command, "find")).isEqualTo("bar"); // command wins
  }

  @Test public void getCollectionName_notAllowListedCommandAndCollectionField() {
    BsonDocument command = new BsonDocument(Arrays.asList(
      new BsonElement("collection", new BsonString("coll")),
      new BsonElement("cmd", new BsonString("bar"))
    ));
    assertThat(listener.getCollectionName(command, "cmd")).isEqualTo("coll"); // collection field wins
  }

  @Test public void getNonEmptyBsonString_null() {
    assertThat(getNonEmptyBsonString(null)).isNull();
  }

  @Test public void getNonEmptyBsonString_notString() {
    assertThat(getNonEmptyBsonString(BsonBoolean.TRUE)).isNull();
  }

  @Test public void getNonEmptyBsonString_empty() {
    assertThat(getNonEmptyBsonString(new BsonString("  "))).isNull();
  }

  @Test public void getNonEmptyBsonString_normal() {
    assertThat(getNonEmptyBsonString(new BsonString(" foo  "))).isEqualTo("foo");
  }

  @Test public void getSpanName_emptyCollectionName() {
    assertThat(getSpanName("foo", null)).isEqualTo("foo");
  }

  @Test public void getSpanName_presentCollectionName() {
    assertThat(getSpanName("foo", "bar")).isEqualTo("foo bar");
  }

  @Test public void commandStarted_noopSpan() {
    when(threadLocalSpan.next()).thenReturn(span);
    when(span.isNoop()).thenReturn(true);

    listener.commandStarted(createCommandStartedEvent());

    verify(threadLocalSpan).next();
    verify(span).isNoop();
    verifyNoMoreInteractions(threadLocalSpan, span);
  }

  @Test public void commandStarted_normal() {
    setupCommandStartedMocks();

    listener.commandStarted(createCommandStartedEvent());

    verifyCommandStartedMocks();
    verifyNoMoreInteractions(threadLocalSpan, span);
  }

  @Test public void commandSucceeded_withoutCommandStarted() {
    listener.commandSucceeded(createCommandSucceededEvent());

    verify(threadLocalSpan).remove();
    verifyNoMoreInteractions(threadLocalSpan);
  }

  @Test public void commandSucceeded_normal() {
    setupCommandStartedMocks();

    listener.commandStarted(createCommandStartedEvent());

    when(threadLocalSpan.remove()).thenReturn(span);

    listener.commandSucceeded(createCommandSucceededEvent());

    verifyCommandStartedMocks();
    verify(threadLocalSpan).remove();
    verify(span).finish();
    verifyNoMoreInteractions(threadLocalSpan);
  }

  @Test public void commandFailed_withoutCommandStarted() {
    listener.commandFailed(createCommandFailedEvent(EXCEPTION));

    verify(threadLocalSpan).remove();
    verifyNoMoreInteractions(threadLocalSpan);
  }

  @Test public void commandFailed_nullThrowable() {
    setupCommandStartedMocks();
    listener.commandStarted(createCommandStartedEvent());

    when(threadLocalSpan.remove()).thenReturn(span);
    when(span.error(any(MongoException.class))).thenReturn(span);

    listener.commandFailed(createCommandFailedEvent(null));

    verifyCommandStartedMocks();
    verify(threadLocalSpan).remove();
    verify(span).error(any(MongoException.class));
    verify(span).finish();
    verifyNoMoreInteractions(threadLocalSpan);
  }

  @Test public void commandFailed_normal() {
    setupCommandStartedMocks();

    listener.commandStarted(createCommandStartedEvent());

    when(threadLocalSpan.remove()).thenReturn(span);
    when(span.error(EXCEPTION)).thenReturn(span);

    listener.commandFailed(createCommandFailedEvent(EXCEPTION));

    verifyCommandStartedMocks();
    verify(threadLocalSpan).remove();
    verify(span).error(EXCEPTION);
    verify(span).finish();
    verifyNoMoreInteractions(threadLocalSpan);
  }

  void setupCommandStartedMocks() {
    when(threadLocalSpan.next()).thenReturn(span);
    when(span.isNoop()).thenReturn(false);
    when(span.name("insert myCollection")).thenReturn(span);
    when(span.kind(Span.Kind.CLIENT)).thenReturn(span);
    when(span.remoteServiceName("mongodb-dbName")).thenReturn(span);
    when(span.tag("mongodb.command", "insert")).thenReturn(span);
    when(span.tag("mongodb.collection", "myCollection")).thenReturn(span);
    when(span.tag(eq("mongodb.cluster_id"), anyString())).thenReturn(span);
    when(span.remoteIpAndPort("127.0.0.1", 27017)).thenReturn(true);
    when(span.start()).thenReturn(span);
  }

  void verifyCommandStartedMocks() {
    verify(threadLocalSpan).next();
    verify(span).isNoop();
    verify(span).name("insert myCollection");
    verify(span).kind(Span.Kind.CLIENT);
    verify(span).remoteServiceName("mongodb-dbName");
    verify(span).tag("mongodb.command", "insert");
    verify(span).tag("mongodb.collection", "myCollection");
    verify(span).tag(eq("mongodb.cluster_id"), anyString());
    verify(span).remoteIpAndPort("127.0.0.1", 27017);
    verify(span).start();
  }

  CommandStartedEvent createCommandStartedEvent() {
    return new CommandStartedEvent(
      1,
      createConnectionDescription(),
      "dbName",
      "insert",
      LONG_COMMAND
    );
  }

  CommandSucceededEvent createCommandSucceededEvent() {
    return new CommandSucceededEvent(
      1,
      createConnectionDescription(),
      "insert",
      new BsonDocument(),
      1000
    );
  }

  CommandFailedEvent createCommandFailedEvent(@Nullable Throwable throwable) {
    return new CommandFailedEvent(
      1,
      createConnectionDescription(),
      "insert",
      2000,
      throwable
    );
  }

  ConnectionDescription createConnectionDescription() {
    return new ConnectionDescription(new ServerId(new ClusterId(), new ServerAddress()));
  }
}
