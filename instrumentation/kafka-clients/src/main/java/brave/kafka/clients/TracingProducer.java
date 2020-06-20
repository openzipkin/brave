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
package brave.kafka.clients;

import brave.Span;
import brave.Tracer;
import brave.internal.Nullable;
import brave.messaging.MessagingRequest;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.SamplerFunction;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.ProducerFencedException;

final class TracingProducer<K, V> implements Producer<K, V> {
  final Producer<K, V> delegate;
  final KafkaTracing kafkaTracing;
  final CurrentTraceContext currentTraceContext;
  final Tracer tracer;
  final Extractor<KafkaProducerRequest> extractor;
  final SamplerFunction<MessagingRequest> sampler;
  final Injector<KafkaProducerRequest> injector;
  @Nullable final String remoteServiceName;

  TracingProducer(Producer<K, V> delegate, KafkaTracing kafkaTracing) {
    this.delegate = delegate;
    this.kafkaTracing = kafkaTracing;
    this.currentTraceContext = kafkaTracing.messagingTracing.tracing().currentTraceContext();
    this.tracer = kafkaTracing.messagingTracing.tracing().tracer();
    this.extractor = kafkaTracing.producerExtractor;
    this.sampler = kafkaTracing.producerSampler;
    this.injector = kafkaTracing.producerInjector;
    this.remoteServiceName = kafkaTracing.remoteServiceName;
  }

  @Override public void initTransactions() {
    delegate.initTransactions();
  }

  @Override public void beginTransaction() {
    delegate.beginTransaction();
  }

  @Override public void commitTransaction() {
    delegate.commitTransaction();
  }

  @Override public void abortTransaction() {
    delegate.abortTransaction();
  }

  /**
   * Send with a callback is always called for KafkaProducer. We do the same here to enable
   * tracing.
   */
  @Override public Future<RecordMetadata> send(ProducerRecord<K, V> record) {
    return this.send(record, null);
  }

  /**
   * This wraps the send method to add tracing.
   *
   * <p>When there is no current span, this attempts to extract one from headers. This is possible
   * when a call to produce a message happens directly after a tracing consumer received a span. One
   * example scenario is Kafka Streams instrumentation.
   */
  @Override
  public Future<RecordMetadata> send(ProducerRecord<K, V> record, @Nullable Callback callback) {
    KafkaProducerRequest request = new KafkaProducerRequest(record);

    TraceContext maybeParent = currentTraceContext.get();
    // Unlike message consumers, we try current span before trying extraction. This is the proper
    // order because the span in scope should take precedence over a potentially stale header entry.
    //
    // NOTE: Brave instrumentation used properly does not result in stale header entries, as we
    // always clear message headers after reading.
    Span span;
    if (maybeParent == null) {
      TraceContextOrSamplingFlags extracted =
        kafkaTracing.extractAndClearTraceIdHeaders(extractor, request, record.headers());
      span = kafkaTracing.nextMessagingSpan(sampler, request, extracted);
    } else { // If we have a span in scope assume headers were cleared before
      span = tracer.newChild(maybeParent);
    }

    if (!span.isNoop()) {
      span.kind(Span.Kind.PRODUCER).name("send");
      if (remoteServiceName != null) span.remoteServiceName(remoteServiceName);
      if (record.key() instanceof String && !"".equals(record.key())) {
        span.tag(KafkaTags.KAFKA_KEY_TAG, record.key().toString());
      }
      span.tag(KafkaTags.KAFKA_TOPIC_TAG, record.topic());
      span.start();
    }

    injector.inject(span.context(), request);

    Tracer.SpanInScope ws = tracer.withSpanInScope(span);
    Throwable error = null;
    try {
      return delegate.send(record, TracingCallback.create(callback, span, currentTraceContext));
    } catch (RuntimeException | Error e) {
      error = e;
      throw e;
    } finally {
      // finish as an exception means the callback won't finish the span
      if (error != null) span.error(error).finish();
      ws.close();
    }
  }

  @Override public void flush() {
    delegate.flush();
  }

  @Override public List<PartitionInfo> partitionsFor(String topic) {
    return delegate.partitionsFor(topic);
  }

  @Override public Map<MetricName, ? extends Metric> metrics() {
    return delegate.metrics();
  }

  @Override public void close() {
    delegate.close();
  }

  // Do not use @Override annotation to avoid compatibility on deprecated methods
  public void close(long timeout, TimeUnit unit) {
    delegate.close(timeout, unit);
  }

  // Do not use @Override annotation to avoid compatibility issue version < 2.0
  public void close(Duration duration) {
    delegate.close(duration);
  }

  @Override
  public void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> offsets,
    String consumerGroupId) {
    delegate.sendOffsetsToTransaction(offsets, consumerGroupId);
  }

  // Do not use @Override annotation to avoid compatibility issue version < 2.5
  public void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> offsets,
    ConsumerGroupMetadata groupMetadata) throws ProducerFencedException {
    delegate.sendOffsetsToTransaction(offsets, groupMetadata);
  }
}
