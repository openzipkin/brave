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

import brave.messaging.MessagingRuleSampler;
import brave.messaging.MessagingTracing;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import java.util.Collections;
import java.util.Map;
import javax.jms.CompletionListener;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.JMSRuntimeException;
import javax.jms.Message;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import zipkin2.Span;
import zipkin2.Span.Kind;

import static brave.messaging.MessagingRequestMatchers.channelNameEquals;
import static brave.propagation.B3SingleFormat.writeB3SingleFormat;
import static org.assertj.core.api.Assertions.assertThat;

/** When adding tests here, also add to {@link brave.jms.ITJms_2_0_TracingMessageProducer} */
public class ITTracingJMSProducer extends ITJms {
  @Rule public TestName testName = new TestName();
  @Rule public ArtemisJmsTestRule jms = new ArtemisJmsTestRule(testName);

  JMSContext tracedContext;
  JMSProducer producer;
  JMSConsumer consumer;
  JMSContext context;
  Map<String, String> existingProperties = Collections.singletonMap("tx", "1");

  @Before public void setup() {
    context = jms.newContext();
    consumer = context.createConsumer(jms.queue);

    setupTracedProducer(jmsTracing);
  }

  void setupTracedProducer(JmsTracing jmsTracing) {
    if (tracedContext != null) tracedContext.close();
    tracedContext = jmsTracing.connectionFactory(jms.factory)
      .createContext(JMSContext.AUTO_ACKNOWLEDGE);
    producer = tracedContext.createProducer();
    existingProperties.forEach(producer::setProperty);
  }

  @After public void tearDownTraced() {
    tracedContext.close();
  }

  @Test public void should_add_b3_single_property() {
    producer.send(jms.queue, "foo");

    Message received = consumer.receive();
    Span producerSpan = reporter.takeRemoteSpan(Span.Kind.PRODUCER);

    assertThat(propertiesToMap(received))
      .containsAllEntriesOf(existingProperties)
      .containsEntry("b3", producerSpan.traceId() + "-" + producerSpan.id() + "-1");
  }

  @Test public void should_not_serialize_parent_span_id() {
    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      producer.send(jms.queue, "foo");
    }

    Message received = consumer.receive();

    Span producerSpan = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    assertChildOf(producerSpan, parent);

    assertThat(propertiesToMap(received))
      .containsAllEntriesOf(existingProperties)
      .containsEntry("b3", producerSpan.traceId() + "-" + producerSpan.id() + "-1");
  }

  @Test public void should_prefer_current_to_stale_b3_header() {
    producer.setProperty("b3", writeB3SingleFormat(newTraceContext(SamplingFlags.NOT_SAMPLED)));

    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      producer.send(jms.queue, "foo");
    }

    Message received = consumer.receive();

    Span producerSpan = reporter.takeRemoteSpan(Span.Kind.PRODUCER);
    assertChildOf(producerSpan, parent);

    assertThat(propertiesToMap(received))
      .containsAllEntriesOf(existingProperties)
      .containsEntry("b3", producerSpan.traceId() + "-" + producerSpan.id() + "-1");
  }

  @Test public void should_record_properties() {
    producer.send(jms.queue, "foo");

    consumer.receive();

    Span producerSpan = reporter.takeRemoteSpan(Kind.PRODUCER);
    assertThat(producerSpan.name()).isEqualTo("send");
    assertThat(producerSpan.tags()).containsEntry("jms.queue", jms.queueName);
  }

  @Test public void should_record_error() {
    jms.after();

    String message;
    try {
      producer.send(jms.queue, "foo");
      throw new AssertionError("expected to throw");
    } catch (JMSRuntimeException e) {
      message = e.getMessage();
    }

    reporter.takeRemoteSpanWithError(Kind.PRODUCER, message);
  }

  @Test public void should_complete_on_callback() {
    producer.setAsync(new CompletionListener() {
      @Override public void onCompletion(Message message) {
        tracing.tracer().currentSpanCustomizer().tag("onCompletion", "");
      }

      @Override public void onException(Message message, Exception exception) {
        tracing.tracer().currentSpanCustomizer().tag("onException", "");
      }
    });
    producer.send(jms.queue, "foo");

    assertThat(reporter.takeRemoteSpan(Kind.PRODUCER).tags())
      .containsKeys("onCompletion");
  }

  @Test public void customSampler() throws JMSException {
    setupTracedProducer(JmsTracing.create(MessagingTracing.newBuilder(tracing)
      .producerSampler(MessagingRuleSampler.newBuilder()
        .putRule(channelNameEquals(jms.queue.getQueueName()), Sampler.NEVER_SAMPLE)
        .build()).build()));

    producer.send(jms.queue, "foo");

    Message received = consumer.receive();

    assertThat(propertiesToMap(received))
      .containsAllEntriesOf(existingProperties)
      .containsKey("b3")
      // Check that the injected context was not sampled
      .satisfies(m -> assertThat(m.get("b3")).endsWith("-0"));

    // @After will also check that the producer was not sampled
  }

  // TODO: find a way to test error callbacks. See ITJms_2_0_TracingMessageProducer.should_complete_on_error_callback
}
