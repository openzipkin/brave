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
package brave.spring.rabbit;

import brave.handler.MutableSpan;
import brave.messaging.MessagingRuleSampler;
import brave.sampler.Sampler;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static brave.Span.Kind.CONSUMER;
import static brave.Span.Kind.PRODUCER;
import static brave.messaging.MessagingRequestMatchers.channelNameEquals;
import static brave.messaging.MessagingRequestMatchers.operationEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class ITSpringRabbitTracing extends ITSpringRabbit {
  @Test public void propagates_trace_info_across_amqp_from_producer() {
    produceMessage();
    awaitMessageConsumed();

    MutableSpan producerSpan = producerSpanHandler.takeRemoteSpan(PRODUCER);
    assertThat(producerSpan.parentId()).isNull();
    MutableSpan consumerSpan = consumerSpanHandler.takeRemoteSpan(CONSUMER);
    assertChildOf(consumerSpan, producerSpan);
    MutableSpan listenerSpan = consumerSpanHandler.takeLocalSpan();
    assertChildOf(listenerSpan, consumerSpan);
  }

  @Test public void clears_message_headers_after_propagation() {
    produceMessage();
    awaitMessageConsumed();

    Message capturedMessage = awaitMessageConsumed();
    Map<String, Object> headers = capturedMessage.getMessageProperties().getHeaders();
    assertThat(headers.keySet()).containsExactly("not-zipkin-header");

    producerSpanHandler.takeRemoteSpan(PRODUCER);
    consumerSpanHandler.takeRemoteSpan(CONSUMER);
    consumerSpanHandler.takeLocalSpan();
  }

  @Test public void tags_spans_with_exchange_and_routing_key() {
    produceMessage();
    awaitMessageConsumed();

    assertThat(producerSpanHandler.takeRemoteSpan(PRODUCER).tags())
      .isEmpty();

    assertThat(consumerSpanHandler.takeRemoteSpan(CONSUMER).tags()).containsOnly(
      entry("rabbit.exchange", binding.getExchange()),
      entry("rabbit.routing_key", binding.getRoutingKey()),
      entry("rabbit.queue", binding.getDestination())
    );

    assertThat(consumerSpanHandler.takeLocalSpan().tags())
      .isEmpty();
  }

  /** Technical implementation of clock sharing might imply a race. This ensures happens-after */
  @Test public void listenerSpanHappensAfterConsumerSpan() {
    produceMessage();
    awaitMessageConsumed();

    MutableSpan producerSpan = producerSpanHandler.takeRemoteSpan(PRODUCER);
    MutableSpan consumerSpan = consumerSpanHandler.takeRemoteSpan(CONSUMER);
    assertSequential(producerSpan, consumerSpan);
    MutableSpan listenerSpan = consumerSpanHandler.takeLocalSpan();
    assertSequential(consumerSpan, listenerSpan);
  }

  @Test public void creates_dependency_links() {
    produceMessage();
    awaitMessageConsumed();

    MutableSpan producerSpan = producerSpanHandler.takeRemoteSpan(PRODUCER);
    MutableSpan consumerSpan = consumerSpanHandler.takeRemoteSpan(CONSUMER);

    assertThat(producerSpan.localServiceName()).isEqualTo("producer");
    assertThat(producerSpan.remoteServiceName()).isEqualTo("rabbitmq");
    assertThat(consumerSpan.remoteServiceName()).isEqualTo("rabbitmq");
    assertThat(consumerSpan.localServiceName()).isEqualTo("consumer");

    consumerSpanHandler.takeLocalSpan();
  }

  @Test public void tags_spans_with_exchange_and_routing_key_from_default() {
    produceMessageFromDefault();
    awaitMessageConsumed();

    assertThat(producerSpanHandler.takeRemoteSpan(PRODUCER).tags())
      .isEmpty();

    assertThat(consumerSpanHandler.takeRemoteSpan(CONSUMER).tags()).containsOnly(
      entry("rabbit.exchange", binding.getExchange()),
      entry("rabbit.routing_key", binding.getRoutingKey()),
      entry("rabbit.queue", binding.getDestination())
    );

    assertThat(consumerSpanHandler.takeLocalSpan().tags())
      .isEmpty();
  }

  // We will revisit this eventually, but these names mostly match the method names
  @Test public void method_names_as_span_names() {
    produceMessage();
    awaitMessageConsumed();

    assertThat(producerSpanHandler.takeRemoteSpan(PRODUCER).name())
      .isEqualTo("publish");

    assertThat(consumerSpanHandler.takeRemoteSpan(CONSUMER).name())
      .isEqualTo("next-message");

    assertThat(consumerSpanHandler.takeLocalSpan().name())
      .isEqualTo("on-message");
  }

  @Test public void producerSampler() {
    producerSampler = MessagingRuleSampler.newBuilder()
      .putRule(operationEquals("send"), Sampler.NEVER_SAMPLE)
      .build();

    produceMessage();
    awaitMessageConsumed();

    // since the producer was unsampled, the consumer should be unsampled also due to propagation
    // reporter rules verify nothing was reported
  }

  @Test public void consumerSampler() {
    consumerSampler = MessagingRuleSampler.newBuilder()
      .putRule(channelNameEquals(TEST_QUEUE), Sampler.NEVER_SAMPLE)
      .build();

    produceUntracedMessage();
    awaitMessageConsumed();
    // reporter rules verify nothing was reported
  }

  @Test public void batchConsumerTest() {
    produceUntracedMessage(exchange_batch.getName(), binding_batch);
    List<Message> messages = awaitBatchMessageConsumed();
    Map<String, Object> headers = messages.get(0).getMessageProperties().getHeaders();
    assertThat(headers.keySet()).containsExactly("not-zipkin-header");

    assertThat(consumerSpanHandler.takeRemoteSpan(CONSUMER).name())
      .isEqualTo("next-message");
    assertThat(consumerSpanHandler.takeLocalSpan().name())
      .isEqualTo("on-message");
  }

  @Test public void traceContinuesToReply() {
    produceUntracedMessage(TEST_EXCHANGE_REQUEST_REPLY, binding_request);
    awaitReplyMessageConsumed();

    MutableSpan requestConsumerSpan = consumerSpanHandler.takeRemoteSpan(CONSUMER);
    MutableSpan replyProducerSpan = consumerSpanHandler.takeRemoteSpan(PRODUCER);
    MutableSpan requestListenerSpan = consumerSpanHandler.takeLocalSpan();
    MutableSpan replyConsumerSpan = consumerSpanHandler.takeRemoteSpan(CONSUMER);
    MutableSpan replyListenerSpan = consumerSpanHandler.takeLocalSpan();

    assertThat(requestConsumerSpan.parentId()).isNull();
    assertThat(requestListenerSpan.parentId()).isEqualTo(requestConsumerSpan.id());
    assertThat(replyProducerSpan.parentId()).isEqualTo(requestListenerSpan.id());
    assertThat(replyConsumerSpan.parentId()).isEqualTo(replyProducerSpan.id());
    assertThat(replyListenerSpan.parentId()).isEqualTo(replyConsumerSpan.id());

    assertThat(Arrays.asList(
      requestListenerSpan,
      replyProducerSpan,
      replyConsumerSpan,
      replyListenerSpan
    )).extracting(MutableSpan::traceId).containsOnly(requestConsumerSpan.traceId());
  }
}
