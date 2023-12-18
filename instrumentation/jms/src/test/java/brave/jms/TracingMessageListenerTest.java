/*
 * Copyright 2013-2023 The OpenZipkin Authors
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
import brave.propagation.B3Propagation;
import brave.propagation.B3SingleFormat;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import java.io.IOException;
import javax.jms.Message;
import javax.jms.MessageListener;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static brave.Span.Kind.CONSUMER;
import static brave.jms.MessageProperties.setStringProperty;
import static org.apache.activemq.command.ActiveMQDestination.QUEUE_TYPE;
import static org.apache.activemq.command.ActiveMQDestination.createDestination;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

// ported from TracingRabbitListenerAdviceTest
public class TracingMessageListenerTest extends ITJms {
  TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
  MessageListener delegate = mock(MessageListener.class);
  MessageListener tracingMessageListener =
    new TracingMessageListener(delegate, jmsTracing, true);

  @Override @AfterEach protected void close() throws Exception {
    tracing.close();
    super.close();
  }

  @Test void starts_new_trace_if_none_exists() {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    onMessageConsumed(message);

    testSpanHandler.takeRemoteSpan(CONSUMER);
    testSpanHandler.takeLocalSpan();
  }

  @Test void starts_new_trace_if_none_exists_noConsumer() {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage();
    onMessageConsumed(message);

    testSpanHandler.takeLocalSpan();
  }

  @Test void consumer_and_listener_have_names() {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    onMessageConsumed(message);

    assertThat(testSpanHandler.takeRemoteSpan(CONSUMER).name()).isEqualTo("receive");
    assertThat(testSpanHandler.takeLocalSpan().name()).isEqualTo("on-message");
  }

  @Test void listener_has_name() {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage();
    onMessageConsumed(message);

    assertThat(testSpanHandler.takeLocalSpan().name()).isEqualTo("on-message");
  }

  @Test void consumer_has_remote_service_name() {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    onMessageConsumed(message);

    assertThat(testSpanHandler.takeRemoteSpan(CONSUMER).remoteServiceName())
      .isEqualTo(jmsTracing.remoteServiceName);
    testSpanHandler.takeLocalSpan();
  }

  @Test void listener_has_no_remote_service_name() {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage();
    onMessageConsumed(message);

    testSpanHandler.takeLocalSpan();
  }

  @Test void tags_consumer_span_but_not_listener() {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    message.setDestination(createDestination("foo", QUEUE_TYPE));
    onMessageConsumed(message);

    assertThat(testSpanHandler.takeRemoteSpan(CONSUMER).tags()).containsEntry("jms.queue", "foo");
    assertThat(testSpanHandler.takeLocalSpan().tags()).isEmpty();
  }

  @Test void listener_has_no_tags_when_header_present() {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage();
    setStringProperty(message, "b3", B3SingleFormat.writeB3SingleFormatWithoutParentId(parent));
    message.setDestination(createDestination("foo", QUEUE_TYPE));
    onMessageConsumed(message);

    assertThat(testSpanHandler.takeLocalSpan().tags()).isEmpty();
  }

  @Test void consumer_span_starts_before_listener() {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    onMessageConsumed(message);

    MutableSpan consumerSpan = testSpanHandler.takeRemoteSpan(CONSUMER);
    MutableSpan listenerSpan = testSpanHandler.takeLocalSpan();

    assertChildOf(listenerSpan, consumerSpan);
    assertSequential(consumerSpan, listenerSpan);
  }

  @Test void listener_completes() {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage();
    onMessageConsumed(message);

    testSpanHandler.takeLocalSpan(); // implicitly checked
  }

  @Test void continues_parent_trace() {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
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

    ActiveMQTextMessage message = new ActiveMQTextMessage();
    B3Propagation.B3_STRING.injector(SETTER).inject(parent, message);

    onMessageConsumed(message);

    // clearing headers ensures later work doesn't try to use the old parent
    assertNoProperties(message);

    assertChildOf(testSpanHandler.takeLocalSpan(), parent);
  }

  @Test void retains_baggage_headers() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    B3Propagation.B3_STRING.injector(SETTER).inject(parent, message);
    message.setStringProperty(BAGGAGE_FIELD_KEY, "");

    onMessageConsumed(message);

    assertThat(message.getProperties())
      .hasSize(1) // clears b3
      .containsEntry(BAGGAGE_FIELD_KEY, "");

    testSpanHandler.takeRemoteSpan(CONSUMER);
    testSpanHandler.takeLocalSpan();
  }

  @Test void continues_parent_trace_single_header() {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
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

    ActiveMQTextMessage message = new ActiveMQTextMessage();
    setStringProperty(message, "b3", B3SingleFormat.writeB3SingleFormatWithoutParentId(parent));

    onMessageConsumed(message);

    // clearing headers ensures later work doesn't try to use the old parent
    assertNoProperties(message);

    assertChildOf(testSpanHandler.takeLocalSpan(), parent);
  }

  @Test void reports_span_if_consume_fails() {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage();
    RuntimeException error = new RuntimeException("Test exception");
    onMessageConsumeFailed(message, error);

    assertThat(testSpanHandler.takeLocalSpan().error()).isEqualTo(error);
  }

  @Test void listener_reports_span_if_fails() {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    RuntimeException error = new RuntimeException("Test exception");
    onMessageConsumeFailed(message, error);

    testSpanHandler.takeRemoteSpan(CONSUMER);
    assertThat(testSpanHandler.takeLocalSpan().error()).isEqualTo(error);
  }

  @Test void reports_span_if_consume_fails_with_no_message() {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
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
      assertThat(message.getProperties()).isEmpty();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }
}
