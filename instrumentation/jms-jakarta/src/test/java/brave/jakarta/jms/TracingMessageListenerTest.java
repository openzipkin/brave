/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jakarta.jms;

import brave.handler.MutableSpan;
import brave.propagation.B3Propagation;
import brave.propagation.B3SingleFormat;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import java.util.Collections;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.client.impl.ClientMessageImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination.TYPE;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.apache.activemq.artemis.reader.MessageUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

  @BeforeEach void setup() {
    when(clientSession.createMessage(anyByte(), eq(true), eq(0L), anyLong(), eq((byte) 4)))
      .thenReturn(new ClientMessageImpl());
  }

  @Override @AfterEach protected void close() throws Exception {
    tracing.close();
    super.close();
  }

  @Test void starts_new_trace_if_none_exists() {
    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    onMessageConsumed(message);

    testSpanHandler.takeRemoteSpan(CONSUMER);
    testSpanHandler.takeLocalSpan();
  }

  @Test void starts_new_trace_if_none_exists_noConsumer() {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    onMessageConsumed(message);

    testSpanHandler.takeLocalSpan();
  }

  @Test void consumer_and_listener_have_names() {
    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    onMessageConsumed(message);

    assertThat(testSpanHandler.takeRemoteSpan(CONSUMER).name()).isEqualTo("receive");
    assertThat(testSpanHandler.takeLocalSpan().name()).isEqualTo("on-message");
  }

  @Test void listener_has_name() {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    onMessageConsumed(message);

    assertThat(testSpanHandler.takeLocalSpan().name()).isEqualTo("on-message");
  }

  @Test void consumer_has_remote_service_name() {
    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    onMessageConsumed(message);

    assertThat(testSpanHandler.takeRemoteSpan(CONSUMER).remoteServiceName())
      .isEqualTo(jmsTracing.remoteServiceName);
    testSpanHandler.takeLocalSpan();
  }

  @Test void listener_has_no_remote_service_name() {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    onMessageConsumed(message);

    testSpanHandler.takeLocalSpan();
  }

  @Test void tags_consumer_span_but_not_listener() throws JMSException {
    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    message.setJMSDestination(createDestination("foo", TYPE.QUEUE));
    onMessageConsumed(message);

    assertThat(testSpanHandler.takeRemoteSpan(CONSUMER).tags()).containsEntry("jms.queue", "foo");
    assertThat(testSpanHandler.takeLocalSpan().tags()).isEmpty();
  }

  @Test void listener_has_no_tags_when_header_present() throws JMSException {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    setStringProperty(message, "b3", B3SingleFormat.writeB3SingleFormatWithoutParentId(parent));
    message.setJMSDestination(createDestination("foo", TYPE.QUEUE));
    onMessageConsumed(message);

    assertThat(testSpanHandler.takeLocalSpan().tags()).isEmpty();
  }

  @Test void consumer_span_starts_before_listener() {
    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    onMessageConsumed(message);

    MutableSpan consumerSpan = testSpanHandler.takeRemoteSpan(CONSUMER);
    MutableSpan listenerSpan = testSpanHandler.takeLocalSpan();

    assertChildOf(listenerSpan, consumerSpan);
    assertSequential(consumerSpan, listenerSpan);
  }

  @Test void listener_completes() {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    onMessageConsumed(message);

    testSpanHandler.takeLocalSpan(); // implicitly checked
  }

  @Test void continues_parent_trace() {
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

  @Test void listener_continues_parent_trace() {
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

  @Test void retains_baggage_headers() throws Exception {
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

  @Test void continues_parent_trace_single_header() {
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

  @Test void listener_continues_parent_trace_single_header() {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    setStringProperty(message, "b3", B3SingleFormat.writeB3SingleFormatWithoutParentId(parent));

    onMessageConsumed(message);

    // clearing headers ensures later work doesn't try to use the old parent
    assertNoProperties(message);

    assertChildOf(testSpanHandler.takeLocalSpan(), parent);
  }

  @Test void reports_span_if_consume_fails() {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    RuntimeException error = new RuntimeException("Test exception");
    onMessageConsumeFailed(message, error);

    assertThat(testSpanHandler.takeLocalSpan().error()).isEqualTo(error);
  }

  @Test void listener_reports_span_if_fails() {
    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    RuntimeException error = new RuntimeException("Test exception");
    onMessageConsumeFailed(message, error);

    testSpanHandler.takeRemoteSpan(CONSUMER);
    assertThat(testSpanHandler.takeLocalSpan().error()).isEqualTo(error);
  }

  @Test void reports_span_if_consume_fails_with_no_message() {
    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    RuntimeException error = new RuntimeException("Test exception");
    onMessageConsumeFailed(message, error);

    testSpanHandler.takeRemoteSpan(CONSUMER);
    assertThat(testSpanHandler.takeLocalSpan().error()).isEqualTo(error);
  }

  @Test void null_listener_if_delegate_is_null() {
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
