/*
 * Copyright 2013-2024 The OpenZipkin Authors
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
package brave.rocketmq.client;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.messaging.MessagingRequest;
import brave.messaging.MessagingTracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.SamplerFunction;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;

import java.util.Map;

public class RocketMQTracing {

  private static final long defaultSuspendCurrentQueueTimeMillis = 1000;
  private static final int defaultDelayLevelWhenNextConsume = 0;

  public static RocketMQTracing create(Tracing tracing) {
    return new RocketMQTracing(MessagingTracing.create(tracing), RocketMQTags.ROCKETMQ_SERVICE);
  }

  public static RocketMQTracing create(MessagingTracing messagingTracing) {
    return new RocketMQTracing(messagingTracing, RocketMQTags.ROCKETMQ_SERVICE);
  }

  public static RocketMQTracing create(MessagingTracing messagingTracing,
    String remoteServiceName) {
    return new RocketMQTracing(messagingTracing, remoteServiceName);
  }

  final Tracing tracing;
  final Tracer tracer;
  final Extractor<TracingProducerRequest> producerExtractor;
  final Extractor<TracingConsumerRequest> consumerExtractor;
  final Injector<TracingProducerRequest> producerInjector;
  final Injector<TracingConsumerRequest> consumerInjector;
  final String[] traceIdHeaders;
  final SamplerFunction<MessagingRequest> producerSampler, consumerSampler;
  final String remoteServiceName;

  RocketMQTracing(MessagingTracing messagingTracing,
    String remoteServiceName) { // intentionally hidden constructor
    this.tracing = messagingTracing.tracing();
    this.tracer = tracing.tracer();
    Propagation<String> propagation = messagingTracing.propagation();
    this.producerExtractor = propagation.extractor(TracingProducerRequest.GETTER);
    this.consumerExtractor = propagation.extractor(TracingConsumerRequest.GETTER);
    this.producerInjector = propagation.injector(TracingProducerRequest.SETTER);
    this.consumerInjector = propagation.injector(TracingConsumerRequest.SETTER);
    this.producerSampler = messagingTracing.producerSampler();
    this.consumerSampler = messagingTracing.consumerSampler();
    this.remoteServiceName = remoteServiceName;

    // We clear the trace ID headers, so that a stale consumer span is not preferred over current
    // listener. We intentionally don't clear BaggagePropagation.allKeyNames as doing so will
    // application fields "user_id" or "country_code"
    this.traceIdHeaders = propagation.keys().toArray(new String[0]);
  }

  <R> TraceContextOrSamplingFlags extractAndClearTraceIdHeaders(Extractor<R> extractor,
    R request,
    Map<String, String> properties) {
    TraceContextOrSamplingFlags extracted = extractor.extract(request);
    // Clear any propagation keys present in the headers
    if (extracted.samplingFlags() == null) { // then trace IDs were extracted
      if (properties != null) {
        clearTraceIdHeaders(properties);
      }
    }
    return extracted;
  }

  /** Creates a potentially noop remote span representing this request. */
  Span nextMessagingSpan(SamplerFunction<MessagingRequest> sampler, MessagingRequest request,
    TraceContextOrSamplingFlags extracted) {
    Boolean sampled = extracted.sampled();
    // only recreate the context if the messaging sampler made a decision
    if (sampled == null && (sampled = sampler.trySample(request)) != null) {
      extracted = extracted.sampled(sampled);
    }
    return tracer.nextSpan(extracted);
  }

  // We can't just skip clearing headers we use because we might inject B3 single, yet have stale B3
  // multi, or vice versa.
  void clearTraceIdHeaders(Map<String, String> headers) {
    for (String traceIDHeader : traceIdHeaders)
      headers.remove(traceIDHeader);
  }

  public Tracing tracing() {
    return tracing;
  }

  public Tracer tracer() {
    return tracer;
  }

  public MessageListenerOrderly wrap(MessageListenerOrderly messageListenerOrderly) {
    return new TracingMessageListenerOrderly(defaultSuspendCurrentQueueTimeMillis, this, messageListenerOrderly);
  }

  public MessageListenerOrderly wrap(long suspendCurrentQueueTimeMillis, MessageListenerOrderly messageListenerOrderly) {
    return new TracingMessageListenerOrderly(suspendCurrentQueueTimeMillis, this, messageListenerOrderly);
  }

  public MessageListenerConcurrently wrap(MessageListenerConcurrently messageListenerConcurrently) {
    return new TracingMessageListenerConcurrently(defaultDelayLevelWhenNextConsume, this, messageListenerConcurrently);
  }

  public MessageListenerConcurrently wrap(int delayLevelWhenNextConsume, MessageListenerConcurrently messageListenerConcurrently) {
    return new TracingMessageListenerConcurrently(delayLevelWhenNextConsume, this, messageListenerConcurrently);
  }

  public MessageListenerOrderly unwrap(MessageListenerOrderly messageListenerOrderly) {
    if (messageListenerOrderly instanceof TracingMessageListenerOrderly) {
      return ((TracingMessageListenerOrderly)messageListenerOrderly).messageListenerOrderly;
    }
    return null;
  }

  public MessageListenerConcurrently unwrap(MessageListenerConcurrently messageListenerConcurrently) {
    if (messageListenerConcurrently instanceof TracingMessageListenerConcurrently) {
      return ((TracingMessageListenerConcurrently)messageListenerConcurrently).messageListenerConcurrently;
    }
    return null;
  }

}
