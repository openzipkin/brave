/*
 * Copyright 2013-2022 The OpenZipkin Authors
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
package brave.jakarta.jms;

import brave.handler.MutableSpan;
import brave.propagation.B3Propagation;
import brave.propagation.B3SingleFormat;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import java.util.Collections;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

import org.apache.activemq.artemis.jms.client.ActiveMQDestination.TYPE;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.client.impl.ClientMessageImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.apache.activemq.artemis.reader.MessageUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static brave.Span.Kind.CONSUMER;
import static brave.jakarta.jms.MessageProperties.setStringProperty;
import static org.apache.activemq.artemis.jms.client.ActiveMQDestination.createDestination;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// ported from TracingRabbitListenerAdviceTest
public class TracingMessageListenerTest extends ITJms {
  TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
  MessageListener delegate = mock(MessageListener.class);
  ClientSession clientSession = mock(ClientSession.class);
  MessageListener tracingMessageListener =
    new TracingMessageListener(delegate, jmsTracing, true);

  @Before public void setup() throws JMSException {
    when(clientSession.createMessage(anyByte(), eq(true), eq(0L), anyLong(), eq((byte)4)))
      .thenReturn(new ClientMessageImpl());
  }

  @After public void close() {
    tracing.close();
  }

  @Test public void starts_new_trace_if_none_exists() {
    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    onMessageConsumed(message);

    testSpanHandler.takeRemoteSpan(CONSUMER);
    testSpanHandler.takeLocalSpan();
  }

  @Test public void starts_new_trace_if_none_exists_noConsumer() {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    onMessageConsumed(message);

    testSpanHandler.takeLocalSpan();
  }

  @Test public void consumer_and_listener_have_names() {
    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    onMessageConsumed(message);

    assertThat(testSpanHandler.takeRemoteSpan(CONSUMER).name()).isEqualTo("receive");
    assertThat(testSpanHandler.takeLocalSpan().name()).isEqualTo("on-message");
  }

  @Test public void listener_has_name() {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    onMessageConsumed(message);

    assertThat(testSpanHandler.takeLocalSpan().name()).isEqualTo("on-message");
  }

  @Test public void consumer_has_remote_service_name() {
    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    onMessageConsumed(message);

    assertThat(testSpanHandler.takeRemoteSpan(CONSUMER).remoteServiceName())
      .isEqualTo(jmsTracing.remoteServiceName);
    testSpanHandler.takeLocalSpan();
  }

  @Test public void listener_has_no_remote_service_name() {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    onMessageConsumed(message);

    testSpanHandler.takeLocalSpan();
  }

  @Test public void tags_consumer_span_but_not_listener() throws JMSException {
    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    message.setJMSDestination(createDestination("foo", TYPE.QUEUE));
    onMessageConsumed(message);

    assertThat(testSpanHandler.takeRemoteSpan(CONSUMER).tags()).containsEntry("jms.queue", "foo");
    assertThat(testSpanHandler.takeLocalSpan().tags()).isEmpty();
  }

  @Test public void listener_has_no_tags_when_header_present() throws JMSException {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    setStringProperty(message, "b3", B3SingleFormat.writeB3SingleFormatWithoutParentId(parent));
    message.setJMSDestination(createDestination("foo", TYPE.QUEUE));
    onMessageConsumed(message);

    assertThat(testSpanHandler.takeLocalSpan().tags()).isEmpty();
  }

  @Test public void consumer_span_starts_before_listener() {
    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    onMessageConsumed(message);

    MutableSpan consumerSpan = testSpanHandler.takeRemoteSpan(CONSUMER);
    MutableSpan listenerSpan = testSpanHandler.takeLocalSpan();

    assertChildOf(listenerSpan, consumerSpan);
    assertSequential(consumerSpan, listenerSpan);
  }

  @Test public void listener_completes() {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    onMessageConsumed(message);

    testSpanHandler.takeLocalSpan(); // implicitly checked
  }

  @Test public void continues_parent_trace() {
    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession) {
        @Override
        public void setStringProperty(final String name, final String value) throws JMSException {
           // Skipping property name validation check
           MessageUtil.setStringProperty(message, name, value);
        }
    };
    B3Propagation.B3_STRING.injector(SETTER).inject(parent, message);

    onMessageConsumed(message);

    // clearing headers ensures later work doesn't try to use the old parent
    assertNoProperties(message);

    MutableSpan consumerSpan = testSpanHandler.takeRemoteSpan(CONSUMER);
    MutableSpan listenerSpan = testSpanHandler.takeLocalSpan();

    assertChildOf(consumerSpan, parent);
    assertChildOf(listenerSpan, consumerSpan);
  }

  @Test public void listener_continues_parent_trace() {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession) {
        @Override
        public void setStringProperty(final String name, final String value) throws JMSException {
           // Skipping property name validation check
           MessageUtil.setStringProperty(message, name, value);
        }
    };
    B3Propagation.B3_STRING.injector(SETTER).inject(parent, message);

    onMessageConsumed(message);

    // clearing headers ensures later work doesn't try to use the old parent
    assertNoProperties(message);

    assertChildOf(testSpanHandler.takeLocalSpan(), parent);
  }

  @Test public void retains_baggage_headers() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    B3Propagation.B3_STRING.injector(SETTER).inject(parent, message);
    message.setStringProperty(BAGGAGE_FIELD_KEY, "");

    onMessageConsumed(message);

    assertThat(Collections.list(message.getPropertyNames()))
      .hasSize(2) // clears b3
      .contains(BAGGAGE_FIELD_KEY)
      .contains(MessageUtil.JMSXDELIVERYCOUNT); /* always added by getPropertyNames() */

    testSpanHandler.takeRemoteSpan(CONSUMER);
    testSpanHandler.takeLocalSpan();
  }

  @Test public void continues_parent_trace_single_header() {
    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    setStringProperty(message, "b3", B3SingleFormat.writeB3SingleFormatWithoutParentId(parent));

    onMessageConsumed(message);

    // clearing headers ensures later work doesn't try to use the old parent
    assertNoProperties(message);

    MutableSpan consumerSpan = testSpanHandler.takeRemoteSpan(CONSUMER);
    MutableSpan listenerSpan = testSpanHandler.takeLocalSpan();

    assertChildOf(consumerSpan, parent);
    assertChildOf(listenerSpan, consumerSpan);
  }

  @Test public void listener_continues_parent_trace_single_header() {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    setStringProperty(message, "b3", B3SingleFormat.writeB3SingleFormatWithoutParentId(parent));

    onMessageConsumed(message);

    // clearing headers ensures later work doesn't try to use the old parent
    assertNoProperties(message);

    assertChildOf(testSpanHandler.takeLocalSpan(), parent);
  }

  @Test public void reports_span_if_consume_fails() {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    RuntimeException error = new RuntimeException("Test exception");
    onMessageConsumeFailed(message, error);

    assertThat(testSpanHandler.takeLocalSpan().error()).isEqualTo(error);
  }

  @Test public void listener_reports_span_if_fails() {
    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    RuntimeException error = new RuntimeException("Test exception");
    onMessageConsumeFailed(message, error);

    testSpanHandler.takeRemoteSpan(CONSUMER);
    assertThat(testSpanHandler.takeLocalSpan().error()).isEqualTo(error);
  }

  @Test public void reports_span_if_consume_fails_with_no_message() {
    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    RuntimeException error = new RuntimeException("Test exception");
    onMessageConsumeFailed(message, error);

    testSpanHandler.takeRemoteSpan(CONSUMER);
    assertThat(testSpanHandler.takeLocalSpan().error()).isEqualTo(error);
  }

  @Test public void null_listener_if_delegate_is_null() {
    assertThat(TracingMessageListener.create(null, jmsTracing))
      .isNull();
  }

  void onMessageConsumed(Message message) {
    doNothing().when(delegate).onMessage(message);
    tracingMessageListener.onMessage(message);
  }

  void onMessageConsumeFailed(Message message, Throwable throwable) {
    doThrow(throwable).when(delegate).onMessage(message);

    assertThatThrownBy(() -> tracingMessageListener.onMessage(message))
      .isSameAs(throwable);
  }

  void assertNoProperties(ActiveMQTextMessage message) {
    try {
      assertThat(Collections.list(message.getPropertyNames()))
        .containsOnly(MessageUtil.JMSXDELIVERYCOUNT); /* always added by getPropertyNames() */
    } catch (JMSException e) {
      throw new AssertionError(e);
    }
  }
}
