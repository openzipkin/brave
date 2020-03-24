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
import brave.sampler.Sampler;
import java.util.concurrent.CountDownLatch;
import javax.jms.CompletionListener;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.TestName;
import zipkin2.Span;

import static brave.messaging.MessagingRequestMatchers.channelNameEquals;
import static org.assertj.core.api.Assertions.assertThat;

/** When adding tests here, also add to {@link brave.jms.ITTracingJMSProducer} */
public class ITJms_2_0_TracingMessageProducer extends ITJms_1_1_TracingMessageProducer {

  @Override JmsTestRule newJmsTestRule(TestName testName) {
    return new ArtemisJmsTestRule(testName);
  }

  @Test public void should_complete_on_callback() throws Exception {
    should_complete_on_callback(
      listener -> messageProducer.send(jms.destination, message, listener));
  }

  @Test public void should_complete_on_callback_queue() throws Exception {
    should_complete_on_callback(
      listener -> queueSender.send(jms.queue, message, listener));
  }

  @Test public void should_complete_on_callback_topic() throws Exception {
    should_complete_on_callback(
      listener -> topicPublisher.send(jms.topic, message, listener));
  }

  void should_complete_on_callback(JMSAsync async) throws Exception {
    async.send(new CompletionListener() {
      @Override public void onCompletion(Message message) {
        tracing.tracer().currentSpanCustomizer().tag("onCompletion", "");
      }

      @Override public void onException(Message message, Exception exception) {
        tracing.tracer().currentSpanCustomizer().tag("onException", "");
      }
    });

    assertThat(takeRemoteSpan(Span.Kind.PRODUCER).tags())
      .containsKey("onCompletion");
  }

  @Test
  @Ignore("https://issues.apache.org/jira/browse/ARTEMIS-2054")
  public void should_complete_on_error_callback() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);

    // To force error to be on callback thread, we need to wait until message is
    // queued. Only then, shutdown the session.
    try (MessageProducer producer = jms.session.createProducer(jms.queue)) {
      producer.send(jms.queue, message, new CompletionListener() {
        @Override public void onCompletion(Message message) {
          try {
            latch.await();
          } catch (InterruptedException e) {
          }
        }

        @Override public void onException(Message message, Exception exception) {
        }
      });
    }

    // If we hang here, this means the above send is not actually non-blocking!
    // Assuming messages are sent sequentially in a queue, the below should block until the forme
    // went through.
    queueSender.send(jms.queue, message, new CompletionListener() {
      @Override public void onCompletion(Message message) {
        tracing.tracer().currentSpanCustomizer().tag("onCompletion", "");
      }

      @Override public void onException(Message message, Exception exception) {
        tracing.tracer().currentSpanCustomizer().tag("onException", "");
      }
    });

    jms.after();
    latch.countDown();

    takeRemoteSpanWithError(Span.Kind.PRODUCER, "onException");
  }

  @Test public void customSampler() throws Exception {
    MessagingRuleSampler producerSampler = MessagingRuleSampler.newBuilder()
      .putRule(channelNameEquals(jms.queue.getQueueName()), Sampler.NEVER_SAMPLE)
      .build();

    try (MessagingTracing messagingTracing = MessagingTracing.newBuilder(tracing)
      .producerSampler(producerSampler)
      .build();
         JMSContext context = JmsTracing.create(messagingTracing)
           .connectionFactory(((ArtemisJmsTestRule) jms).factory)
           .createContext(JMSContext.AUTO_ACKNOWLEDGE);
    ) {
      context.createProducer().send(jms.queue, "foo");
    }

    Message received = queueReceiver.receive();

    assertThat(propertiesToMap(received)).containsKey("b3")
      // Check that the injected context was not sampled
      .satisfies(m -> assertThat(m.get("b3")).endsWith("-0"));

    // @After will also check that the producer was not sampled
  }

  interface JMSAsync {
    void send(CompletionListener listener) throws JMSException;
  }
}
