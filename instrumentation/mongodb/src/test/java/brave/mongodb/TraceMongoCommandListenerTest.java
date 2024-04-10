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

import brave.Span;
import brave.propagation.ThreadLocalSpan;
import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ServerId;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import java.util.Arrays;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonElement;
import org.bson.BsonString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static brave.mongodb.TraceMongoCommandListener.getNonEmptyBsonString;
import static brave.mongodb.TraceMongoCommandListener.getSpanName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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

  @BeforeEach void setUp() {
    listener = new TraceMongoCommandListener(threadLocalSpan);
  }

  @Test void getCollectionName_missingCommand() {
    assertThat(listener.getCollectionName(new BsonDocument(), "find")).isNull();
  }

  @Test void getCollectionName_notStringCommandArgument() {
    assertThat(
      listener.getCollectionName(new BsonDocument("find", BsonBoolean.TRUE), "find")).isNull();
  }

  @Test void getCollectionName_emptyStringCommandArgument() {
    assertThat(
      listener.getCollectionName(new BsonDocument("find", new BsonString("  ")), "find")).isNull();
  }

  @Test void getCollectionName_notAllowListedCommand() {
    assertThat(
      listener.getCollectionName(new BsonDocument("cmd", new BsonString(" bar ")), "cmd")).isNull();
  }

  @Test void getCollectionName_allowListedCommand() {
    assertThat(listener.getCollectionName(new BsonDocument("find", new BsonString(" bar ")),
      "find")).isEqualTo("bar");
  }

  @Test void getCollectionName_collectionFieldOnly() {
    assertThat(listener.getCollectionName(new BsonDocument("collection", new BsonString(" bar ")),
      "find")).isEqualTo("bar");
  }

  @Test void getCollectionName_allowListedCommandAndCollectionField() {
    BsonDocument command = new BsonDocument(Arrays.asList(
      new BsonElement("collection", new BsonString("coll")),
      new BsonElement("find", new BsonString("bar"))
    ));
    assertThat(listener.getCollectionName(command, "find")).isEqualTo("bar"); // command wins
  }

  @Test void getCollectionName_notAllowListedCommandAndCollectionField() {
    BsonDocument command = new BsonDocument(Arrays.asList(
      new BsonElement("collection", new BsonString("coll")),
      new BsonElement("cmd", new BsonString("bar"))
    ));
    assertThat(listener.getCollectionName(command, "cmd")).isEqualTo(
      "coll"); // collection field wins
  }

  @Test void getNonEmptyBsonString_null() {
    assertThat(getNonEmptyBsonString(null)).isNull();
  }

  @Test void getNonEmptyBsonString_notString() {
    assertThat(getNonEmptyBsonString(BsonBoolean.TRUE)).isNull();
  }

  @Test void getNonEmptyBsonString_empty() {
    assertThat(getNonEmptyBsonString(new BsonString("  "))).isNull();
  }

  @Test void getNonEmptyBsonString_normal() {
    assertThat(getNonEmptyBsonString(new BsonString(" foo  "))).isEqualTo("foo");
  }

  @Test void getSpanName_emptyCollectionName() {
    assertThat(getSpanName("foo", null)).isEqualTo("foo");
  }

  @Test void getSpanName_presentCollectionName() {
    assertThat(getSpanName("foo", "bar")).isEqualTo("foo bar");
  }

  @Test void commandStarted_noopSpan() {
    when(threadLocalSpan.next()).thenReturn(span);
    when(span.isNoop()).thenReturn(true);

    listener.commandStarted(createCommandStartedEvent());

    verify(threadLocalSpan).next();
    verify(span).isNoop();
    verifyNoMoreInteractions(threadLocalSpan, span);
  }

  @Test void commandStarted_normal() {
    setupCommandStartedMocks();

    listener.commandStarted(createCommandStartedEvent());

    verifyCommandStartedMocks();
    verifyNoMoreInteractions(threadLocalSpan, span);
  }

  @Test void commandSucceeded_withoutCommandStarted() {
    listener.commandSucceeded(createCommandSucceededEvent());

    verify(threadLocalSpan).remove();
    verifyNoMoreInteractions(threadLocalSpan);
  }

  @Test void commandSucceeded_normal() {
    setupCommandStartedMocks();

    listener.commandStarted(createCommandStartedEvent());

    when(threadLocalSpan.remove()).thenReturn(span);

    listener.commandSucceeded(createCommandSucceededEvent());

    verifyCommandStartedMocks();
    verify(threadLocalSpan).remove();
    verify(span).finish();
    verifyNoMoreInteractions(threadLocalSpan);
  }

  @Test void commandFailed_withoutCommandStarted() {
    listener.commandFailed(createCommandFailedEvent(EXCEPTION));

    verify(threadLocalSpan).remove();
    verifyNoMoreInteractions(threadLocalSpan);
  }

  @Test void commandFailed_normal() {
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
    CommandStartedEvent event = mock(CommandStartedEvent.class);
    lenient().when(event.getRequestId()).thenReturn(1);
    lenient().when(event.getConnectionDescription()).thenReturn(createConnectionDescription());
    when(event.getDatabaseName()).thenReturn("dbName");
    lenient().when(event.getCommandName()).thenReturn("insert");
    lenient().when(event.getCommand()).thenReturn(LONG_COMMAND);
    return event;
  }

  CommandSucceededEvent createCommandSucceededEvent() {
    return mock(CommandSucceededEvent.class);
  }

  CommandFailedEvent createCommandFailedEvent(Throwable throwable) {
    CommandFailedEvent event = mock(CommandFailedEvent.class);
    lenient().when(event.getThrowable()).thenReturn(throwable);
    return event;
  }

  ConnectionDescription createConnectionDescription() {
    return new ConnectionDescription(new ServerId(new ClusterId(), new ServerAddress()));
  }
}
