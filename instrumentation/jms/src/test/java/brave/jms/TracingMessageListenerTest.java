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

import brave.propagation.B3Propagation;
import brave.propagation.B3SingleFormat;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import javax.jms.Message;
import javax.jms.MessageListener;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.junit.After;
import org.junit.Test;
import zipkin2.Span;

import static brave.jms.MessagePropagation.SETTER;
import static org.apache.activemq.command.ActiveMQDestination.QUEUE_TYPE;
import static org.apache.activemq.command.ActiveMQDestination.createDestination;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static zipkin2.Span.Kind.CONSUMER;

// ported from TracingRabbitListenerAdviceTest
public class TracingMessageListenerTest extends ITJms {
  TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
  MessageListener delegate = mock(MessageListener.class);
  MessageListener tracingMessageListener =
    new TracingMessageListener(delegate, jmsTracing, true);

  @After public void close() {
    tracing.close();
  }

  @Test public void starts_new_trace_if_none_exists() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    onMessageConsumed(message);

    takeRemoteSpan(CONSUMER);
    takeLocalSpan();
  }

  @Test public void starts_new_trace_if_none_exists_noConsumer() throws Exception {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage();
    onMessageConsumed(message);

    takeLocalSpan();
  }

  @Test public void consumer_and_listener_have_names() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    onMessageConsumed(message);

    assertThat(takeRemoteSpan(CONSUMER).name()).isEqualTo("receive");
    assertThat(takeLocalSpan().name()).isEqualTo("on-message");
  }

  @Test public void listener_has_name() throws Exception {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage();
    onMessageConsumed(message);

    assertThat(takeLocalSpan().name()).isEqualTo("on-message");
  }

  @Test public void consumer_has_remote_service_name() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    onMessageConsumed(message);

    assertThat(takeRemoteSpan(CONSUMER).remoteServiceName())
      .isEqualTo(jmsTracing.remoteServiceName);
    takeLocalSpan();
  }

  @Test public void listener_has_no_remote_service_name() throws Exception {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage();
    onMessageConsumed(message);

    takeLocalSpan();
  }

  @Test public void tags_consumer_span_but_not_listener() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    message.setDestination(createDestination("foo", QUEUE_TYPE));
    onMessageConsumed(message);

    assertThat(takeRemoteSpan(CONSUMER).tags()).containsEntry("jms.queue", "foo");
    assertThat(takeLocalSpan().tags()).isEmpty();
  }

  @Test public void listener_has_no_tags_when_header_present() throws Exception {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage();
    SETTER.put(message, "b3", B3SingleFormat.writeB3SingleFormatWithoutParentId(parent));
    message.setDestination(createDestination("foo", QUEUE_TYPE));
    onMessageConsumed(message);

    assertThat(takeLocalSpan().tags()).isEmpty();
  }

  @Test public void consumer_span_starts_before_listener() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    onMessageConsumed(message);

    Span consumerSpan = takeRemoteSpan(Span.Kind.CONSUMER), listenerSpan = takeLocalSpan();
    assertChildOf(listenerSpan, consumerSpan);
    assertSequential(consumerSpan, listenerSpan);
  }

  @Test public void listener_completes() throws Exception {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage();
    onMessageConsumed(message);

    takeLocalSpan(); // implicitly checked
  }

  @Test public void continues_parent_trace() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    B3Propagation.B3_STRING.injector(SETTER).inject(parent, message);

    onMessageConsumed(message);

    // clearing headers ensures later work doesn't try to use the old parent
    assertThat(message.getProperties()).isEmpty();

    Span consumerSpan = takeRemoteSpan(Span.Kind.CONSUMER), listenerSpan = takeLocalSpan();
    assertChildOf(consumerSpan, parent);
    assertChildOf(listenerSpan, consumerSpan);
  }

  @Test public void listener_continues_parent_trace() throws Exception {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage();
    B3Propagation.B3_STRING.injector(SETTER).inject(parent, message);

    onMessageConsumed(message);

    // clearing headers ensures later work doesn't try to use the old parent
    assertThat(message.getProperties()).isEmpty();

    assertChildOf(takeLocalSpan(), parent);
  }

  @Test public void continues_parent_trace_single_header() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    SETTER.put(message, "b3", B3SingleFormat.writeB3SingleFormatWithoutParentId(parent));

    onMessageConsumed(message);

    // clearing headers ensures later work doesn't try to use the old parent
    assertThat(message.getProperties()).isEmpty();

    Span consumerSpan = takeRemoteSpan(Span.Kind.CONSUMER), listenerSpan = takeLocalSpan();
    assertChildOf(consumerSpan, parent);
    assertChildOf(listenerSpan, consumerSpan);
  }

  @Test public void listener_continues_parent_trace_single_header() throws Exception {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage();
    SETTER.put(message, "b3", B3SingleFormat.writeB3SingleFormatWithoutParentId(parent));

    onMessageConsumed(message);

    // clearing headers ensures later work doesn't try to use the old parent
    assertThat(message.getProperties()).isEmpty();

    assertChildOf(takeLocalSpan(), parent);
  }

  @Test public void reports_span_if_consume_fails() throws Exception {
    tracingMessageListener =
      new TracingMessageListener(delegate, jmsTracing, false);

    ActiveMQTextMessage message = new ActiveMQTextMessage();
    onMessageConsumeFailed(message, new RuntimeException("expected exception"));

    takeLocalSpanWithError("expected exception");
  }

  @Test public void listener_reports_span_if_fails() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    onMessageConsumeFailed(message, new RuntimeException("expected exception"));

    takeRemoteSpan(CONSUMER);
    takeLocalSpanWithError("expected exception");
  }

  @Test public void reports_span_if_consume_fails_with_no_message() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    onMessageConsumeFailed(message, new RuntimeException());

    takeRemoteSpan(CONSUMER);
    takeLocalSpanWithError("RuntimeException");
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
}
