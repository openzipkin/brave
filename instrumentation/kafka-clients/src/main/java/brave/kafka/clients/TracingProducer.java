/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
import brave.Tracing;
import brave.internal.Nullable;
import brave.messaging.MessagingAdapter;
import brave.messaging.MessagingParser;
import brave.messaging.ProducerHandler;
import brave.propagation.CurrentTraceContext.Scope;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Headers;

final class TracingProducer<K, V> implements Producer<K, V> {

  final Producer<K, V> delegate;
  final KafkaTracing kafkaTracing;
  final Tracing tracing;
  final ProducerHandler<String, ProducerRecord, Headers> handler;
  final MessagingParser parser;

  TracingProducer(Producer<K, V> delegate, KafkaTracing kafkaTracing) {
    this.delegate = delegate;
    this.handler = kafkaTracing.producerHandler;
    this.kafkaTracing = kafkaTracing;
    this.tracing = kafkaTracing.messagingTracing.tracing();
    this.parser = kafkaTracing.messagingTracing.parser();
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
    return send(record, null);
  }

  /**
   * This wraps the send method to add tracing.
   *
   * <p>When there is no current span, this attempts to extract one from headers. This is possible
   * when a call to produce a message happens directly after a tracing producer received a span. One
   * example scenario is Kafka Streams instrumentation.
   */
  // TODO: make b3single an option and then note how using this minimizes overhead
  @Override
  public Future<RecordMetadata> send(ProducerRecord<K, V> record, @Nullable Callback callback) {
    Span span = handler.startSend(record.topic(), record);
    if (span.isNoop()) return delegate.send(record, callback);

    FinishSpanCallback finishSpanCallback = tracingCallback(record, callback, span);
    try (Tracer.SpanInScope ws = tracing.tracer().withSpanInScope(span)) {
      return delegate.send(record, finishSpanCallback);
    } catch (RuntimeException | Error e) {
      finishSpanCallback.finish(e); // an exception might imply the callback was not invoked
      throw e;
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

  @Override public void close(long timeout, TimeUnit unit) {
    delegate.close(timeout, unit);
  }

  // Do not use @Override annotation to avoid compatibility issue version < 2.0
  public void close(Duration duration) {
    delegate.close(duration);
  }

  @Override
  public void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> offsets,
    String producerGroupId) {
    delegate.sendOffsetsToTransaction(offsets, producerGroupId);
  }

  /**
   * Decorates, then finishes a producer span. Allows tracing to record the duration between
   * batching for send and actual send.
   */
  FinishSpanCallback tracingCallback(ProducerRecord<K, V> record, @Nullable Callback delegate,
    Span span) {
    if (delegate == null) return new FinishSpanCallback(record, span);
    return new DelegateAndFinishSpanCallback(record, delegate, span);
  }

  final class DelegateAndFinishSpanCallback extends FinishSpanCallback {
    final Callback delegate;

    DelegateAndFinishSpanCallback(ProducerRecord<K, V> record, Callback delegate, Span span) {
      super(record, span);
      this.delegate = delegate;
    }

    @Override public void onCompletion(RecordMetadata metadata, @Nullable Exception exception) {
      try (Scope ws = tracing.currentTraceContext().maybeScope(span.context())) {
        delegate.onCompletion(metadata, exception);
      } finally {
        finish(exception);
      }
    }
  }

  class FinishSpanCallback implements Callback {
    final ProducerRecord<K, V> record;
    final Span span;

    FinishSpanCallback(ProducerRecord<K, V> record, Span span) {
      this.record = record;
      this.span = span;
    }

    @Override public void onCompletion(RecordMetadata metadata, @Nullable Exception exception) {
      finish(exception);
    }

    void finish(@Nullable Throwable error) {
      if (error != null) span.error(error);
      handler.finishSend(record.topic(), record, span);
      span.finish();
    }
  }

  static final class ProducerRecordAdapter
    extends MessagingAdapter<String, ProducerRecord, Headers> {
    final String remoteServiceName;

    ProducerRecordAdapter(String remoteServiceName) {
      this.remoteServiceName = remoteServiceName;
    }

    @Override public Headers carrier(ProducerRecord message) {
      return message.headers();
    }

    @Override public String channel(String topic) {
      return topic;
    }

    @Override public String channelKind(String channel) {
      return "topic";
    }

    @Override public String messageKey(ProducerRecord message) {
      return KafkaTracing.recordKey(message.key());
    }

    @Override public String correlationId(ProducerRecord message) {
      return null;
    }

    @Override public String brokerName(String topic) {
      return remoteServiceName;
    }
  }
}
