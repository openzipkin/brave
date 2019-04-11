package brave.kafka.clients;

import brave.Span;
import brave.Tracer;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
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
  final CurrentTraceContext current;
  final Tracer tracer;
  final Injector<Headers> injector;
  final Extractor<Headers> extractor;
  @Nullable final String remoteServiceName;

  TracingProducer(Producer<K, V> delegate, KafkaTracing kafkaTracing) {
    this.delegate = delegate;
    this.kafkaTracing = kafkaTracing;
    this.current = kafkaTracing.tracing.currentTraceContext();
    this.tracer = kafkaTracing.tracing.tracer();
    this.injector = kafkaTracing.injector;
    this.extractor = kafkaTracing.extractor;
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
  // TODO: make b3single an option and then note how using this minimizes overhead
  @Override
  public Future<RecordMetadata> send(ProducerRecord<K, V> record, @Nullable Callback callback) {
    TraceContext maybeParent = current.get();
    // Unlike message consumers, we try current span before trying extraction. This is the proper
    // order because the span in scope should take precedence over a potentially stale header entry.
    //
    // NOTE: Brave instrumentation used properly does not result in stale header entries, as we
    // always clear message headers after reading.
    Span span;
    if (maybeParent == null) {
      span = tracer.nextSpan(kafkaTracing.extractAndClearHeaders(record.headers()));
    } else {
      // If we have a span in scope assume headers were cleared before
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

    injector.inject(span.context(), record.headers());

    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
      return delegate.send(record, TracingCallback.create(callback, span, current));
    } catch (RuntimeException | Error e) {
      span.error(e).finish(); // finish as an exception means the callback won't finish the span
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
      String consumerGroupId) {
    delegate.sendOffsetsToTransaction(offsets, consumerGroupId);
  }
}
