/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
package brave.jms;

import brave.ScopedSpan;
import brave.propagation.TraceContext;
import java.util.Collections;
import java.util.Map;
import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import zipkin2.Span;

import static brave.jms.JmsTracing.SETTER;
import static brave.propagation.B3SingleFormat.writeB3SingleFormat;
import static org.assertj.core.api.Assertions.assertThat;

/** When adding tests here, also add to {@linkplain brave.jms.ITTracingJMSProducer} */
public class ITJms_1_1_TracingMessageProducer extends JmsTest {
  @Rule public TestName testName = new TestName();
  @Rule public JmsTestRule jms = newJmsTestRule(testName);

  Session tracedSession;
  MessageProducer messageProducer;
  MessageConsumer messageConsumer;

  QueueSession tracedQueueSession;
  QueueSender queueSender;
  QueueReceiver queueReceiver;

  TopicSession tracedTopicSession;
  TopicPublisher topicPublisher;
  TopicSubscriber topicSubscriber;

  TextMessage message;
  BytesMessage bytesMessage;

  Map<String, String> existingProperties = Collections.singletonMap("tx", "1");

  JmsTestRule newJmsTestRule(TestName testName) {
    return new JmsTestRule.ActiveMQ(testName);
  }

  @Before public void setup() throws Exception {
    tracedSession = jmsTracing.connection(jms.connection)
      .createSession(false, Session.AUTO_ACKNOWLEDGE);
    tracedQueueSession = jmsTracing.queueConnection(jms.queueConnection)
      .createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
    tracedTopicSession = jmsTracing.topicConnection(jms.topicConnection)
      .createTopicSession(false, Session.AUTO_ACKNOWLEDGE);

    messageProducer = tracedSession.createProducer(null /* to test explicit destination */);
    messageConsumer = jms.session.createConsumer(jms.destination);

    queueSender = tracedQueueSession.createSender(null /* to test explicit queue */);
    queueReceiver = jms.queueSession.createReceiver(jms.queue);

    topicPublisher = tracedTopicSession.createPublisher(null /* to test explicit topic */);
    topicSubscriber = jms.topicSession.createSubscriber(jms.topic);

    message = jms.newMessage("foo");
    for (Map.Entry<String, String> existingProperty : existingProperties.entrySet()) {
      SETTER.put(message, existingProperty.getKey(), existingProperty.getValue());
    }
    bytesMessage = jms.newBytesMessage("foo");
    for (Map.Entry<String, String> existingProperty : existingProperties.entrySet()) {
      SETTER.put(bytesMessage, existingProperty.getKey(), existingProperty.getValue());
    }
  }

  @After public void tearDownTraced() throws JMSException {
    tracedSession.close();
    tracedQueueSession.close();
    tracedTopicSession.close();
  }

  @Test public void should_add_b3_single_property() throws Exception {
    messageProducer.send(jms.destination, message);
    assertHasB3SingleProperty(messageConsumer.receive());
  }

  @Test public void should_add_b3_single_property_bytes() throws Exception {
    messageProducer.send(jms.destination, bytesMessage);
    assertHasB3SingleProperty(messageConsumer.receive());
  }

  @Test public void should_add_b3_single_property_queue() throws Exception {
    queueSender.send(jms.queue, message);
    assertHasB3SingleProperty(queueReceiver.receive());
  }

  @Test public void should_add_b3_single_property_topic() throws Exception {
    topicPublisher.publish(jms.topic, message);
    assertHasB3SingleProperty(topicSubscriber.receive());
  }

  void assertHasB3SingleProperty(Message received) throws Exception {
    Span producerSpan = takeSpan();

    assertThat(propertiesToMap(received))
      .containsAllEntriesOf(existingProperties)
      .containsEntry("b3", producerSpan.traceId() + "-" + producerSpan.id() + "-1");
  }

  @Test public void should_not_serialize_parent_span_id() throws Exception {
    ScopedSpan parent = tracing.tracer().startScopedSpan("main");
    try {
      messageProducer.send(jms.destination, message);
    } finally {
      parent.finish();
    }

    Message received = messageConsumer.receive();
    Span producerSpan = takeSpan(), parentSpan = takeSpan();
    assertThat(producerSpan.parentId()).isEqualTo(parentSpan.id());

    assertThat(propertiesToMap(received))
      .containsAllEntriesOf(existingProperties)
      .containsEntry("b3", producerSpan.traceId() + "-" + producerSpan.id() + "-1");
  }

  @Test public void should_prefer_current_to_stale_b3_header() throws Exception {
    jms.setReadOnlyProperties(message, false);
    SETTER.put(message, "b3",
      writeB3SingleFormat(TraceContext.newBuilder().traceId(1).spanId(1).build()));

    ScopedSpan parent = tracing.tracer().startScopedSpan("main");
    try {
      messageProducer.send(jms.destination, message);
    } finally {
      parent.finish();
    }

    Message received = messageConsumer.receive();
    Span producerSpan = takeSpan(), parentSpan = takeSpan();
    assertThat(producerSpan.parentId()).isEqualTo(parentSpan.id());

    assertThat(propertiesToMap(received))
      .containsAllEntriesOf(existingProperties)
      .containsEntry("b3", producerSpan.traceId() + "-" + producerSpan.id() + "-1");
  }

  @Test public void should_record_properties() throws Exception {
    messageProducer.send(jms.destination, message);
    should_record_properties(Collections.singletonMap("jms.queue", jms.destinationName));
  }

  @Test public void should_record_properties_queue() throws Exception {
    queueSender.send(jms.queue, message);
    should_record_properties(Collections.singletonMap("jms.queue", jms.queueName));
  }

  @Test public void should_record_properties_topic() throws Exception {
    topicPublisher.send(jms.topic, message);
    should_record_properties(Collections.singletonMap("jms.topic", jms.topicName));
  }

  void should_record_properties(Map<String, String> producerTags) throws Exception {
    Span producerSpan = takeSpan();
    assertThat(producerSpan.name()).isEqualTo("send");
    assertThat(producerSpan.kind()).isEqualTo(Span.Kind.PRODUCER);
    assertThat(producerSpan.timestampAsLong()).isPositive();
    assertThat(producerSpan.durationAsLong()).isPositive();
    assertThat(producerSpan.tags()).isEqualTo(producerTags);
  }

  @Test public void should_record_error() throws Exception {
    should_record_error(() -> messageProducer.send(message));
  }

  @Test public void should_record_error_queue() throws Exception {
    should_record_error(() -> queueSender.send(message));
  }

  @Test public void should_record_error_topic() throws Exception {
    should_record_error(() -> topicPublisher.send(message));
  }

  void should_record_error(JMSRunnable send) throws Exception {
    tracedSession.close();

    try {
      send.run();
    } catch (Exception e) {
    }

    assertThat(takeSpan().tags()).containsKey("error");
  }
}
