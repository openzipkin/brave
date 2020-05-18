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
package brave.jms;

import brave.handler.MutableSpan;
import brave.messaging.MessagingRuleSampler;
import brave.messaging.MessagingTracing;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
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

import static brave.Span.Kind.PRODUCER;
import static brave.jms.MessageProperties.setStringProperty;
import static brave.messaging.MessagingRequestMatchers.channelNameEquals;
import static brave.propagation.B3SingleFormat.writeB3SingleFormat;
import static org.assertj.core.api.Assertions.assertThat;

/** When adding tests here, also add to {@link brave.jms.ITTracingJMSProducer} */
public class ITJms_1_1_TracingMessageProducer extends ITJms {
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

  @Before public void setup() throws JMSException {
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
      setStringProperty(message, existingProperty.getKey(), existingProperty.getValue());
    }
    bytesMessage = jms.newBytesMessage("foo");
    for (Map.Entry<String, String> existingProperty : existingProperties.entrySet()) {
      setStringProperty(bytesMessage, existingProperty.getKey(), existingProperty.getValue());
    }
  }

  @After public void tearDownTraced() throws JMSException {
    tracedSession.close();
    tracedQueueSession.close();
    tracedTopicSession.close();
  }

  @Test public void should_add_b3_single_property() throws JMSException {
    messageProducer.send(jms.destination, message);
    assertHasB3SingleProperty(messageConsumer.receive());
  }

  @Test public void should_add_b3_single_property_bytes() throws JMSException {
    messageProducer.send(jms.destination, bytesMessage);
    assertHasB3SingleProperty(messageConsumer.receive());
  }

  @Test public void should_add_b3_single_property_queue() throws JMSException {
    queueSender.send(jms.queue, message);
    assertHasB3SingleProperty(queueReceiver.receive());
  }

  @Test public void should_add_b3_single_property_topic() throws JMSException {
    topicPublisher.publish(jms.topic, message);
    assertHasB3SingleProperty(topicSubscriber.receive());
  }

  void assertHasB3SingleProperty(Message received) throws JMSException {
    MutableSpan producerSpan = testSpanHandler.takeRemoteSpan(PRODUCER);

    assertThat(propertiesToMap(received))
      .containsAllEntriesOf(existingProperties)
      .containsEntry("b3", producerSpan.traceId() + "-" + producerSpan.id() + "-1");
  }

  @Test public void should_not_serialize_parent_span_id() throws JMSException {
    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      messageProducer.send(jms.destination, message);
    }

    Message received = messageConsumer.receive();

    MutableSpan producerSpan = testSpanHandler.takeRemoteSpan(PRODUCER);
    assertChildOf(producerSpan, parent);

    assertThat(propertiesToMap(received))
      .containsAllEntriesOf(existingProperties)
      .containsEntry("b3", producerSpan.traceId() + "-" + producerSpan.id() + "-1");
  }

  @Test public void should_prefer_current_to_stale_b3_header() throws JMSException {
    jms.setReadOnlyProperties(message, false);
    setStringProperty(message, "b3", writeB3SingleFormat(newTraceContext(SamplingFlags.NOT_SAMPLED)));

    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      messageProducer.send(jms.destination, message);
    }

    Message received = messageConsumer.receive();

    MutableSpan producerSpan = testSpanHandler.takeRemoteSpan(PRODUCER);
    assertChildOf(producerSpan, parent);

    assertThat(propertiesToMap(received))
      .containsAllEntriesOf(existingProperties)
      .containsEntry("b3", producerSpan.traceId() + "-" + producerSpan.id() + "-1");
  }

  @Test public void should_record_properties() throws JMSException {
    messageProducer.send(jms.destination, message);
    should_record_properties(Collections.singletonMap("jms.queue", jms.destinationName));
  }

  @Test public void should_record_properties_queue() throws JMSException {
    queueSender.send(jms.queue, message);
    should_record_properties(Collections.singletonMap("jms.queue", jms.queueName));
  }

  @Test public void should_record_properties_topic() throws JMSException {
    topicPublisher.send(jms.topic, message);
    should_record_properties(Collections.singletonMap("jms.topic", jms.topicName));
  }

  void should_record_properties(Map<String, String> producerTags) {
    MutableSpan producerSpan = testSpanHandler.takeRemoteSpan(PRODUCER);
    assertThat(producerSpan.name()).isEqualTo("send");
    assertThat(producerSpan.tags()).containsAllEntriesOf(producerTags);
  }

  @Test public void should_set_error() throws JMSException {
    tracedSession.close();
    should_set_error(() -> messageProducer.send(jms.destination, message));
  }

  @Test public void should_set_error_queue() throws JMSException {
    tracedQueueSession.close();
    should_set_error(() -> queueSender.send(jms.queue, message));
  }

  @Test public void should_set_error_topic() throws JMSException {
    tracedTopicSession.close();
    should_set_error(() -> topicPublisher.send(jms.topic, message));
  }

  void should_set_error(JMSRunnable send) {
    String message;
    try {
      send.run();
      throw new AssertionError("expected to throw");
    } catch (JMSException e) {
      message = e.getMessage();
    }

    testSpanHandler.takeRemoteSpanWithErrorMessage(PRODUCER, message);
  }

  @Test public void customSampler() throws JMSException {
    queueSender.close();
    tracedQueueSession.close();

    MessagingRuleSampler producerSampler = MessagingRuleSampler.newBuilder()
      .putRule(channelNameEquals(jms.queue.getQueueName()), Sampler.NEVER_SAMPLE)
      .build();

    try (MessagingTracing messagingTracing = MessagingTracing.newBuilder(tracing)
      .producerSampler(producerSampler)
      .build()) {
      // JMS 1.1 didn't have auto-closeable. tearDownTraced closes these
      tracedQueueSession = JmsTracing.create(messagingTracing)
        .queueConnection(jms.queueConnection)
        .createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
      queueSender = tracedQueueSession.createSender(jms.queue);

      queueSender.send(message);
    }

    Message received = queueReceiver.receive();

    assertThat(propertiesToMap(received)).containsKey("b3")
      // Check that the injected context was not sampled
      .satisfies(m -> assertThat(m.get("b3")).endsWith("-0"));

    // @After will also check that the producer was not sampled
  }
}
