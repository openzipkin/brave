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
import jakarta.jms.JMSConsumer;
import jakarta.jms.Message;
import java.util.Collections;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.client.impl.ClientMessageImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static brave.Span.Kind.CONSUMER;
import static brave.propagation.B3SingleFormat.parseB3SingleFormat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TracingJMSConsumerTest extends ITJms {
  TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
  JMSConsumer delegate = mock(JMSConsumer.class);
  JMSConsumer tracingJMSConsumer = new TracingJMSConsumer(delegate, null, jmsTracing);
  ClientSession clientSession = mock(ClientSession.class);

  @BeforeEach void setup() {
    when(clientSession.createMessage(anyByte(), eq(true), eq(0L), anyLong(), eq((byte) 4)))
      .thenReturn(new ClientMessageImpl());
  }

  @Override @AfterEach protected void close() throws Exception {
    tracing.close();
    super.close();
  }

  @Test void receive_creates_consumer_span() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    receive(message);

    MutableSpan consumer = testSpanHandler.takeRemoteSpan(CONSUMER);
    assertThat(consumer.name()).isEqualTo("receive");
    assertThat(consumer.name()).isEqualTo("receive");
  }

  @Test void receive_continues_parent_trace_single_header() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    message.setStringProperty("b3", B3SingleFormat.writeB3SingleFormatWithoutParentId(parent));

    receive(message);

    // Ensure the current span in on the message, not the parent
    MutableSpan consumer = testSpanHandler.takeRemoteSpan(CONSUMER);
    assertChildOf(consumer, parent);

    TraceContext messageContext = parseB3SingleFormat(message.getStringProperty("b3")).context();
    assertThat(messageContext.traceIdString()).isEqualTo(consumer.traceId());
    assertThat(messageContext.spanIdString()).isEqualTo(consumer.id());
  }

  @Test void receive_retains_baggage_properties() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage(clientSession);
    B3Propagation.B3_STRING.injector(SETTER).inject(parent, message);
    message.setStringProperty(BAGGAGE_FIELD_KEY, "");

    receive(message);

    assertThat(Collections.list(message.getPropertyNames()))
      .contains(BAGGAGE_FIELD_KEY);

    testSpanHandler.takeRemoteSpan(CONSUMER);
  }

  void receive(Message message) {
    when(delegate.receive()).thenReturn(message);
    tracingJMSConsumer.receive();
  }
}
