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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.springframework.amqp.core.Message;
import zipkin2.DependencyLink;
import zipkin2.Span;
import zipkin2.internal.DependencyLinker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.groups.Tuple.tuple;
import static zipkin2.Span.Kind.CONSUMER;
import static zipkin2.Span.Kind.PRODUCER;

public class ITSpringRabbitTracing extends ITSpringRabbit {
  @Test public void propagates_trace_info_across_amqp_from_producer() {
    produceMessage();
    awaitMessageConsumed();

    Span producerSpan = producerReporter.takeRemoteSpan(PRODUCER);
    assertThat(producerSpan.parentId()).isNull();
    Span consumerSpan = consumerReporter.takeRemoteSpan(CONSUMER);
    assertChildOf(consumerSpan, producerSpan);
    Span listenerSpan = consumerReporter.takeLocalSpan();
    assertChildOf(listenerSpan, consumerSpan);
  }

  @Test public void clears_message_headers_after_propagation() {
    produceMessage();
    awaitMessageConsumed();

    Message capturedMessage = awaitMessageConsumed();
    Map<String, Object> headers = capturedMessage.getMessageProperties().getHeaders();
    assertThat(headers.keySet()).containsExactly("not-zipkin-header");

    producerReporter.takeRemoteSpan(PRODUCER);
    consumerReporter.takeRemoteSpan(CONSUMER);
    consumerReporter.takeLocalSpan();
  }

  @Test public void tags_spans_with_exchange_and_routing_key() {
    produceMessage();
    awaitMessageConsumed();

    assertThat(producerReporter.takeRemoteSpan(PRODUCER).tags())
      .isEmpty();

    assertThat(consumerReporter.takeRemoteSpan(CONSUMER).tags()).containsOnly(
      entry("rabbit.exchange", binding.getExchange()),
      entry("rabbit.routing_key", binding.getRoutingKey()),
      entry("rabbit.queue", binding.getDestination())
    );

    assertThat(consumerReporter.takeLocalSpan().tags())
      .isEmpty();
  }

  /** Technical implementation of clock sharing might imply a race. This ensures happens-after */
  @Test public void listenerSpanHappensAfterConsumerSpan() {
    produceMessage();
    awaitMessageConsumed();

    Span producerSpan = producerReporter.takeRemoteSpan(PRODUCER);
    Span consumerSpan = consumerReporter.takeRemoteSpan(CONSUMER);
    assertSequential(producerSpan, consumerSpan);
    Span listenerSpan = consumerReporter.takeLocalSpan();
    assertSequential(consumerSpan, listenerSpan);
  }

  @Test public void creates_dependency_links() {
    produceMessage();
    awaitMessageConsumed();

    List<Span> allSpans = Arrays.asList(
      producerReporter.takeRemoteSpan(PRODUCER),
      consumerReporter.takeRemoteSpan(CONSUMER),
      consumerReporter.takeLocalSpan()
    );

    List<DependencyLink> links = new DependencyLinker().putTrace(allSpans).link();
    assertThat(links).extracting("parent", "child").containsExactly(
      tuple("producer", "rabbitmq"),
      tuple("rabbitmq", "consumer")
    );
  }

  @Test public void tags_spans_with_exchange_and_routing_key_from_default() {
    produceMessageFromDefault();
    awaitMessageConsumed();

    assertThat(producerReporter.takeRemoteSpan(PRODUCER).tags())
      .isEmpty();

    assertThat(consumerReporter.takeRemoteSpan(CONSUMER).tags()).containsOnly(
      entry("rabbit.exchange", binding.getExchange()),
      entry("rabbit.routing_key", binding.getRoutingKey()),
      entry("rabbit.queue", binding.getDestination())
    );

    assertThat(consumerReporter.takeLocalSpan().tags())
      .isEmpty();
  }

  // We will revisit this eventually, but these names mostly match the method names
  @Test public void method_names_as_span_names() {
    produceMessage();
    awaitMessageConsumed();

    assertThat(producerReporter.takeRemoteSpan(PRODUCER).name())
      .isEqualTo("publish");

    assertThat(consumerReporter.takeRemoteSpan(CONSUMER).name())
      .isEqualTo("next-message");

    assertThat(consumerReporter.takeLocalSpan().name())
      .isEqualTo("on-message");
  }
}
