package brave.kafka.clients;

import brave.Span;
import brave.Span.Kind;
import brave.Tracer;
import brave.Tracing;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class TracingProducer<K, V> implements Producer<K, V> {

  final Tracing tracing;
  final TraceContext.Injector<Headers> injector;
  final TraceContext.Extractor<Headers> extractor;
  final Producer<K, V> delegate;
  @Nullable final String remoteServiceName;

  TracingProducer(Tracing tracing, Producer<K, V> delegate, @Nullable String remoteServiceName) {
    this.delegate = delegate;
    this.tracing = tracing;
    this.injector = tracing.propagation().injector(KafkaPropagation.HEADER_SETTER);
    this.extractor = tracing.propagation().extractor(KafkaPropagation.HEADER_GETTER);
    this.remoteServiceName = remoteServiceName;
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
   * We wrap the send method to add tracing.
   * This method is expecting an existing current trace context to create a child span.
   * If there is no current context, an additional operation will be performed to extract
   * context from record headers. This could impact performance if no context is injected
   * on record headers, but is required to propagate context on Kafka Streams instrumentation.
   */
  @Override public Future<RecordMetadata> send(ProducerRecord<K, V> record, @Nullable Callback callback) {
    final Span span;
    final TraceContext maybeParent = tracing.currentTraceContext().get();
    //Only if there is no trace context and there are headers available, additional extract is performed
    if (maybeParent == null && record.headers().toArray().length > 0) {
      TraceContextOrSamplingFlags traceContextOrSamplingFlags = extractor.extract(record.headers());
      span = tracing.tracer().nextSpan(traceContextOrSamplingFlags);
    } else {
      span = tracing.tracer().nextSpan();
    }

    tracing.propagation().keys().forEach(key -> record.headers().remove(key));
    injector.inject(span.context(), record.headers());
    if (!span.isNoop()) {
      if (record.key() instanceof String && !"".equals(record.key())) {
        span.tag(KafkaTags.KAFKA_KEY_TAG, record.key().toString());
      }
      if (remoteServiceName != null) span.remoteServiceName(remoteServiceName);
      span.tag(KafkaTags.KAFKA_TOPIC_TAG, record.topic()).name("send").kind(Kind.PRODUCER).start();
    }
    try (Tracer.SpanInScope ws = tracing.tracer().withSpanInScope(span)) {
      return delegate.send(record, new TracingCallback(span, callback));
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

  @Override
  public void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> offsets,
      String consumerGroupId) {
    delegate.sendOffsetsToTransaction(offsets, consumerGroupId);
  }
}
