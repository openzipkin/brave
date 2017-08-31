package brave.kafka.clients;

import brave.Span;
import brave.Span.Kind;
import brave.Tracing;
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
import zipkin.internal.Nullable;

class TracingProducer<K, V> implements Producer<K, V> {

  private final Tracing tracing;
  private final TraceContext.Injector<ProducerRecord> injector;
  private final Producer<K, V> wrappedProducer;

  TracingProducer(Tracing tracing, Producer<K, V> producer) {
    this.wrappedProducer = producer;
    this.tracing = tracing;
    this.injector =
        tracing.propagation().injector(new KafkaPropagation.ProducerRecordSetter());
  }

  @Override
  public void initTransactions() {
    wrappedProducer.initTransactions();
  }

  @Override
  public void beginTransaction() {
    wrappedProducer.beginTransaction();
  }

  @Override
  public void commitTransaction() {
    wrappedProducer.commitTransaction();
  }

  @Override
  public void abortTransaction() {
    wrappedProducer.abortTransaction();
  }

  /**
   * Send with a callback is always called for KafkaProducer. We do the same here to enable
   * tracing.
   */
  @Override
  public Future<RecordMetadata> send(ProducerRecord<K, V> record) {
    return this.send(record, null);
  }

  /**
   * We wrap the send method to add tracing.
   */
  @Override
  public Future<RecordMetadata> send(ProducerRecord<K, V> record, @Nullable Callback callback) {
    Span span = tracing.tracer().nextSpan();

    if (record.key() != null) {
      span.tag(KafkaTags.KAFKA_KEY_TAG, record.key().toString());
    }
    span.tag(KafkaTags.KAFKA_TOPIC_TAG, record.topic());

    injector.inject(span.context(), record);

    span.kind(Kind.PRODUCER).start();
    TracingCallback tracingCallback = new TracingCallback(span, callback);
    return wrappedProducer.send(record, tracingCallback);
  }

  @Override
  public void flush() {
    wrappedProducer.flush();
  }

  @Override
  public List<PartitionInfo> partitionsFor(String topic) {
    return wrappedProducer.partitionsFor(topic);
  }

  @Override
  public Map<MetricName, ? extends Metric> metrics() {
    return wrappedProducer.metrics();
  }

  @Override
  public void close() {
    wrappedProducer.close();
  }

  @Override
  public void close(long timeout, TimeUnit unit) {
    wrappedProducer.close(timeout, unit);
  }

  @Override
  public void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> offsets,
      String consumerGroupId) {
    wrappedProducer.sendOffsetsToTransaction(offsets, consumerGroupId);
  }
}
