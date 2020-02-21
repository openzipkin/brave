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

import brave.Span;
import brave.Tracer;
import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ServerId;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static brave.mongo.TraceMongoCommandListener.getCollectionName;
import static brave.mongo.TraceMongoCommandListener.getSpanName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TraceMongoCommandListenerTest {
  private static final BsonDocument LONG_COMMAND = BsonDocument.parse("{" +
    "   \"insert\": \"myCollection\",\n" +
    "   \"bar\": {" +
    "       \"test\": \"asdfghjkl\",\n" +
    "   },\n" +
    "}");

  private static final Throwable EXCEPTION = new RuntimeException("Error occurred");

  @Mock private Tracer tracer;

  @Mock private Span span;

  private TraceMongoCommandListener listener;

  @Before public void setUp() {
    listener = TraceMongoCommandListener.builder()
      .tracer(tracer)
      .build();
  }

  @Test public void getCollectionName_emptyCommand() {
    assertThat(getCollectionName(new BsonDocument())).isNotPresent();
  }

  @Test public void getCollectionName_notString() {
    assertThat(getCollectionName(new BsonDocument("foo", BsonBoolean.TRUE))).isNotPresent();
  }

  @Test public void getCollectionName_emptyString() {
    assertThat(getCollectionName(new BsonDocument("foo", new BsonString("  ")))).isNotPresent();
  }

  @Test public void getCollectionName_normal() {
    assertThat(getCollectionName(new BsonDocument("foo", new BsonString(" bar ")))).hasValue("bar");
  }

  @Test public void getAbbreviatedCommand_negativeMaxLength() {
    doGetAbbreviatedCommandTest(-1, Optional.empty());
  }

  @Test public void getAbbreviatedCommand_zeroMaxLength() {
    doGetAbbreviatedCommandTest(0, Optional.empty());
  }

  @Test public void getAbbreviatedCommand_shortMaxLength() {
    doGetAbbreviatedCommandTest(5, Optional.of("{\"ins"));
  }

  @Test public void getAbbreviatedCommand_longMaxLength() {
    doGetAbbreviatedCommandTest(1000,
      Optional.of("{\"insert\": \"myCollection\", \"bar\": {\"test\": \"asdfghjkl\"}}"));
  }

  private void doGetAbbreviatedCommandTest(final int maxAbbreviatedCommandLength, final Optional<String> expectedValue) {
    final TraceMongoCommandListener listener = TraceMongoCommandListener.builder()
      .tracer(tracer)
      .maxAbbreviatedCommandLength(maxAbbreviatedCommandLength)
      .build();
    assertThat(listener.getAbbreviatedCommand(LONG_COMMAND)).isEqualTo(expectedValue);
  }

  @Test public void getSpanName_emptyCollectionName() {
    assertThat(getSpanName("foo", Optional.empty())).isEqualTo("foo");
  }

  @Test public void getSpanName_presentCollectionName() {
    assertThat(getSpanName("foo", Optional.of("bar"))).isEqualTo("foo bar");
  }

  @Test public void builder_missingTracer() {
    try {
      TraceMongoCommandListener.builder().build();
    } catch (final NullPointerException ignored) {
      return;
    }
    fail("Should have thrown NPE due to missing .tracer() call");
  }

  @Test public void builder_setsValuesCorrectly() {
    final int maxAbbreviatedCommandLength = 55;
    final TraceMongoCommandListener listener = TraceMongoCommandListener.builder()
      .tracer(tracer)
      .maxAbbreviatedCommandLength(maxAbbreviatedCommandLength)
      .build();
    assertThat(listener).extracting("tracer").isEqualTo(tracer);
    assertThat(listener).extracting("maxAbbreviatedCommandLength").isEqualTo(maxAbbreviatedCommandLength);
  }

  @Test public void commandStarted_noopSpan() {
    when(tracer.nextSpan()).thenReturn(span);
    when(span.isNoop()).thenReturn(true);

    listener.commandStarted(createCommandStartedEvent());

    verify(tracer).nextSpan();
    verify(span).isNoop();
    verifyNoMoreInteractions(tracer, span);
  }

  @Test public void commandStarted_normal() {
    setupCommandStartedMocks();

    listener.commandStarted(createCommandStartedEvent());

    verifyCommandStartedMocks();
    verifyNoMoreInteractions(tracer, span);
  }

  @Test public void commandSucceeded_withoutCommandStarted() {
    listener.commandSucceeded(createCommandSucceededEvent());

    verifyNoMoreInteractions(tracer);
  }

  @Test public void commandSucceeded_normal() {
    setupCommandStartedMocks();

    listener.commandStarted(createCommandStartedEvent());

    listener.commandSucceeded(createCommandSucceededEvent());

    verifyCommandStartedMocks();
    verify(span).finish();
    verifyNoMoreInteractions(tracer);
  }

  @Test public void commandFailed_withoutCommandStarted() {
    listener.commandFailed(createCommandFailedEvent());
    verifyNoMoreInteractions(tracer);
  }

  @Test public void commandFailed_normal() {
    setupCommandStartedMocks();

    listener.commandStarted(createCommandStartedEvent());

    when(span.error(EXCEPTION)).thenReturn(span);

    listener.commandFailed(createCommandFailedEvent());

    verifyCommandStartedMocks();
    verify(span).finish();
    verifyNoMoreInteractions(tracer);
  }

  private void setupCommandStartedMocks() {
    when(tracer.nextSpan()).thenReturn(span);
    when(span.isNoop()).thenReturn(false);
    when(span.name("insert myCollection")).thenReturn(span);
    when(span.kind(Span.Kind.CLIENT)).thenReturn(span);
    when(span.remoteServiceName("dbName")).thenReturn(span);
    when(span.tag("db.type", "mongo")).thenReturn(span);
    when(span.tag("mongo.database", "dbName")).thenReturn(span);
    when(span.tag("mongo.operation", "insert")).thenReturn(span);
    when(span.tag("mongo.command", "{\"insert\": \"myCollection\", \"bar\": {\"test\": \"asdfghjkl\"}}")).thenReturn(span);
    when(span.tag("mongo.collection", "myCollection")).thenReturn(span);
    when(span.remoteIpAndPort("127.0.0.1", 27017)).thenReturn(true);
    when(span.start()).thenReturn(span);
  }

  private void verifyCommandStartedMocks() {
    verify(tracer).nextSpan();
    verify(span).isNoop();
    verify(span).name("insert myCollection");
    verify(span).kind(Span.Kind.CLIENT);
    verify(span).remoteServiceName("dbName");
    verify(span).tag("db.type", "mongo");
    verify(span).tag("mongo.database", "dbName");
    verify(span).tag("mongo.operation", "insert");
    verify(span).tag("mongo.command", "{\"insert\": \"myCollection\", \"bar\": {\"test\": \"asdfghjkl\"}}");
    verify(span).tag("mongo.collection", "myCollection");
    verify(span).remoteIpAndPort("127.0.0.1", 27017);
    verify(span).start();
  }

  private CommandStartedEvent createCommandStartedEvent() {
    return new CommandStartedEvent(
      1,
      createConnectionDescription(),
      "dbName",
      "insert",
      LONG_COMMAND
    );
  }

  private CommandSucceededEvent createCommandSucceededEvent() {
    return new CommandSucceededEvent(
      1,
      createConnectionDescription(),
      "insert",
      new BsonDocument(),
      1000
    );
  }

  private CommandFailedEvent createCommandFailedEvent() {
    return new CommandFailedEvent(
      1,
      createConnectionDescription(),
      "insert",
      2000,
      EXCEPTION
    );
  }

  private ConnectionDescription createConnectionDescription() {
    return new ConnectionDescription(new ServerId(new ClusterId(), new ServerAddress()));
  }
}
