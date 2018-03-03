package brave.kafka.clients;

import brave.Span;
import brave.Span.Kind;
import brave.Tracer;
import brave.Tracing;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
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

import static brave.kafka.clients.KafkaTracing.finish;

final class TracingProducer<K, V> implements Producer<K, V> {

  final Tracing tracing;
  final TraceContext.Injector<Headers> injector;
  final Producer<K, V> delegate;

  TracingProducer(Tracing tracing, Producer<K, V> delegate) {
    this.delegate = delegate;
    this.tracing = tracing;
    this.injector = tracing.propagation().injector(KafkaPropagation.HEADER_SETTER);
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

  /** We wrap the send method to add tracing. */
  @Override
  public Future<RecordMetadata> send(ProducerRecord<K, V> record, @Nullable Callback callback) {
    Span span = tracing.tracer().nextSpan();
    tracing.propagation().keys().forEach(key -> record.headers().remove(key));
    injector.inject(span.context(), record.headers());
    if (!span.isNoop()) {
      if (record.key() instanceof String && !"".equals(record.key())) {
        span.tag(KafkaTags.KAFKA_KEY_TAG, record.key().toString());
      }
      span.tag(KafkaTags.KAFKA_TOPIC_TAG, record.topic()).name("send").kind(Kind.PRODUCER).start();
    }
    try (Tracer.SpanInScope ws = tracing.tracer().withSpanInScope(span)) {
      return delegate.send(record, new TracingCallback(span, callback));
    } catch (RuntimeException | Error e) {
      finish(span, e);
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

  @Override
  public void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> offsets,
      String consumerGroupId) {
    delegate.sendOffsetsToTransaction(offsets, consumerGroupId);
  }
}
