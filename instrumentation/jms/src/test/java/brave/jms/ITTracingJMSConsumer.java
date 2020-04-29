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

import brave.Tags;
import brave.messaging.MessagingRuleSampler;
import brave.messaging.MessagingTracing;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSProducer;
import javax.jms.Message;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import zipkin2.Span;

import static brave.jms.MessagePropagation.GETTER;
import static brave.messaging.MessagingRequestMatchers.operationEquals;
import static javax.jms.JMSContext.AUTO_ACKNOWLEDGE;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.Span.Kind.CONSUMER;

/**
 * When adding tests here, add tests not already in {@link ITJms_1_1_TracingMessageConsumer} to
 * {@link brave.jms.ITJms_2_0_TracingMessageConsumer}
 */
public class ITTracingJMSConsumer extends ITJms {
  @Rule public TestName testName = new TestName();
  @Rule public ArtemisJmsTestRule jms = new ArtemisJmsTestRule(testName);

  JMSContext tracedContext;
  JMSProducer producer;
  JMSConsumer consumer;
  JMSContext context;

  @Before public void setup() {
    context = jms.newContext();
    producer = context.createProducer();

    setupTracedConsumer(jmsTracing);
  }

  void setupTracedConsumer(JmsTracing jmsTracing) {
    if (consumer != null) consumer.close();
    if (tracedContext != null) tracedContext.close();
    tracedContext = jmsTracing.connectionFactory(jms.factory).createContext(AUTO_ACKNOWLEDGE);
    consumer = tracedContext.createConsumer(jms.queue);
  }

  @After public void tearDownTraced() {
    tracedContext.close();
  }

  @Test public void messageListener_runsAfterConsumer() {
    consumer.setMessageListener(m -> {
    });
    producer.send(jms.queue, "foo");

    Span consumerSpan = reporter.takeRemoteSpan(CONSUMER), listenerSpan = reporter.takeLocalSpan();
    assertChildOf(listenerSpan, consumerSpan);
    assertSequential(consumerSpan, listenerSpan);
  }

  @Test public void messageListener_startsNewTrace() {
    messageListener_startsNewTrace(() -> producer.send(jms.queue, "foo"));
  }

  @Test public void messageListener_startsNewTrace_bytes() {
    messageListener_startsNewTrace(() -> producer.send(jms.queue, new byte[] {1, 2, 3, 4}));
  }

  void messageListener_startsNewTrace(Runnable send) {
    consumer.setMessageListener(m -> {
      tracing.tracer().currentSpanCustomizer().name("message-listener");

      // clearing headers ensures later work doesn't try to use the old parent
      String b3 = GETTER.get(m, "b3");
      tracing.tracer().currentSpanCustomizer().tag("b3", String.valueOf(b3 != null));
    });

    send.run();

    Span consumerSpan = reporter.takeRemoteSpan(CONSUMER), listenerSpan = reporter.takeLocalSpan();

    assertThat(consumerSpan.name()).isEqualTo("receive");
    assertThat(consumerSpan.tags())
      .hasSize(1)
      .containsEntry("jms.queue", jms.queueName);

    assertChildOf(listenerSpan, consumerSpan);
    assertThat(listenerSpan.name()).isEqualTo("message-listener"); // overridden name
    assertThat(listenerSpan.tags())
      .hasSize(1) // no redundant copy of consumer tags
      .containsEntry("b3", "false"); // b3 header not leaked to listener
  }

  @Test public void messageListener_resumesTrace() {
    messageListener_resumesTrace(() -> producer.send(jms.queue, "foo"));
  }

  @Test public void messageListener_resumesTrace_bytes() {
    messageListener_resumesTrace(() -> producer.send(jms.queue, new byte[] {1, 2, 3, 4}));
  }

  void messageListener_resumesTrace(Runnable send) {
    consumer.setMessageListener(m -> {
      // clearing headers ensures later work doesn't try to use the old parent
      String b3 = GETTER.get(m, "b3");
      tracing.tracer().currentSpanCustomizer().tag("b3", String.valueOf(b3 != null));
    });

    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    producer.setProperty("b3", parent.traceIdString() + "-" + parent.spanIdString() + "-1");
    send.run();

    Span consumerSpan = reporter.takeRemoteSpan(CONSUMER), listenerSpan = reporter.takeLocalSpan();
    assertChildOf(consumerSpan, parent);
    assertChildOf(listenerSpan, consumerSpan);

    assertThat(listenerSpan.tags())
      .hasSize(1) // no redundant copy of consumer tags
      .containsEntry("b3", "false"); // b3 header not leaked to listener
  }

  @Test public void messageListener_readsBaggage() {
    messageListener_readsBaggage(() -> producer.send(jms.queue, "foo"));
  }

  @Test public void messageListener_readsBaggage_bytes() {
    messageListener_readsBaggage(() -> producer.send(jms.queue, new byte[] {1, 2, 3, 4}));
  }

  void messageListener_readsBaggage(Runnable send) {
    consumer.setMessageListener(m ->
        Tags.BAGGAGE_FIELD.tag(BAGGAGE_FIELD, tracing.tracer().currentSpan())
    );

    String baggage = "joey";
    producer.setProperty(BAGGAGE_FIELD_KEY, baggage);
    send.run();

    Span consumerSpan = reporter.takeRemoteSpan(CONSUMER), listenerSpan = reporter.takeLocalSpan();
    assertThat(consumerSpan.parentId()).isNull();
    assertChildOf(listenerSpan, consumerSpan);
    assertThat(listenerSpan.tags())
        .containsEntry(BAGGAGE_FIELD.name(), baggage);
  }

  @Test public void receive_startsNewTrace() {
    receive_startsNewTrace(() -> producer.send(jms.queue, "foo"));
  }

  @Test public void receive_startsNewTrace_bytes() {
    receive_startsNewTrace(() -> producer.send(jms.queue, new byte[] {1, 2, 3, 4}));
  }

  void receive_startsNewTrace(Runnable send) {
    send.run();
    consumer.receive();
    Span consumerSpan = reporter.takeRemoteSpan(CONSUMER);
    assertThat(consumerSpan.name()).isEqualTo("receive");
    assertThat(consumerSpan.tags()).containsEntry("jms.queue", jms.queueName);
  }

  @Test public void receive_resumesTrace() {
    receiveResumesTrace(() -> producer.send(jms.queue, "foo"));
  }

  @Test public void receive_resumesTrace_bytes() {
    receiveResumesTrace(() -> producer.send(jms.queue, new byte[] {1, 2, 3, 4}));
  }

  void receiveResumesTrace(Runnable send) {
    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    producer.setProperty("b3", parent.traceIdString() + "-" + parent.spanIdString() + "-1");
    send.run();

    Message received = consumer.receive();

    Span consumerSpan = reporter.takeRemoteSpan(CONSUMER);
    assertChildOf(consumerSpan, parent);

    assertThat(GETTER.get(received, "b3"))
      .isEqualTo(parent.traceIdString() + "-" + consumerSpan.id() + "-1");
  }

  @Test public void receive_customSampler() {
    setupTracedConsumer(JmsTracing.create(MessagingTracing.newBuilder(tracing)
      .consumerSampler(MessagingRuleSampler.newBuilder()
        .putRule(operationEquals("receive"), Sampler.NEVER_SAMPLE)
        .build()).build()));

    producer.send(jms.queue, "foo");

    // Check that the message headers are not sampled
    assertThat(GETTER.get(consumer.receive(), "b3"))
      .endsWith("-0");

    // @After will also check that the consumer was not sampled
  }
}
