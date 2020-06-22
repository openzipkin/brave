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
import brave.propagation.B3Propagation;
import brave.propagation.B3SingleFormat;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import java.io.IOException;
import javax.jms.JMSConsumer;
import javax.jms.Message;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.junit.After;
import org.junit.Test;

import static brave.Span.Kind.CONSUMER;
import static brave.propagation.B3SingleFormat.parseB3SingleFormat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TracingJMSConsumerTest extends ITJms {
  TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
  JMSConsumer delegate = mock(JMSConsumer.class);
  JMSConsumer tracingJMSConsumer = new TracingJMSConsumer(delegate, null, jmsTracing);

  @After public void close() {
    tracing.close();
  }

  @Test public void receive_creates_consumer_span() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    receive(message);

    MutableSpan consumer = testSpanHandler.takeRemoteSpan(CONSUMER);
    assertThat(consumer.name()).isEqualTo("receive");
    assertThat(consumer.name()).isEqualTo("receive");
  }

  @Test public void receive_continues_parent_trace_single_header() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    message.setStringProperty("b3", B3SingleFormat.writeB3SingleFormatWithoutParentId(parent));

    receive(message);

    // Ensure the current span in on the message, not the parent
    MutableSpan consumer = testSpanHandler.takeRemoteSpan(CONSUMER);
    assertChildOf(consumer, parent);

    TraceContext messageContext = parseB3SingleFormat(message.getStringProperty("b3")).context();
    assertThat(messageContext.traceIdString()).isEqualTo(consumer.traceId());
    assertThat(messageContext.spanIdString()).isEqualTo(consumer.id());
  }

  @Test public void receive_retains_baggage_properties() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    B3Propagation.B3_STRING.injector(SETTER).inject(parent, message);
    message.setStringProperty(BAGGAGE_FIELD_KEY, "");

    receive(message);

    assertThat(message.getProperties())
      .containsEntry(BAGGAGE_FIELD_KEY, "");

    testSpanHandler.takeRemoteSpan(CONSUMER);
  }

  void receive(Message message) throws Exception {
    when(delegate.receive()).thenReturn(message);
    tracingJMSConsumer.receive();
  }

  void assertNoProperties(ActiveMQTextMessage message) {
    try {
      assertThat(message.getProperties()).isEmpty();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }
}
