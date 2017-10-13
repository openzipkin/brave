package brave.kafka.clients;

import brave.Span;
import brave.Tracing;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.clients.consumer.OffsetCommitCallback;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Headers;

/**
 * Kafka Consumer decorator. Read records headers to create and complete a child of the incoming
 * producers span if possible.
 */
final class TracingConsumer<K, V> implements Consumer<K, V> {

  final Tracing tracing;
  final Injector<Headers> injector;
  final Extractor<Headers> extractor;
  final Consumer<K, V> delegate;

  TracingConsumer(Tracing tracing, Consumer<K, V> delegate) {
    this.delegate = delegate;
    this.tracing = tracing;
    this.injector = tracing.propagation().injector(KafkaPropagation.HEADER_SETTER);
    this.extractor = tracing.propagation().extractor(KafkaPropagation.HEADER_GETTER);
  }

  /** This */
  @Override public ConsumerRecords<K, V> poll(long timeout) {
    ConsumerRecords<K, V> records = delegate.poll(timeout);
    if (records.isEmpty() || tracing.isNoop()) return records;
    Map<String, Span> consumerSpansForTopic = new LinkedHashMap<>();
    for (TopicPartition partition : records.partitions()) {
      String topic = partition.topic();
      List<ConsumerRecord<K, V>> recordsInPartition = records.records(partition);
      for (int i = 0, length = recordsInPartition.size(); i < length; i++) {
        ConsumerRecord<K, V> record = recordsInPartition.get(i);
        TraceContextOrSamplingFlags extracted = extractor.extract(record.headers());

        // If we extracted neither a trace context, nor request-scoped data (extra),
        // make or reuse a span for this topic
        if (extracted.samplingFlags() != null && extracted.extra().isEmpty()) {
          Span consumerSpanForTopic = consumerSpansForTopic.get(topic);
          if (consumerSpanForTopic == null) {
            consumerSpansForTopic.put(topic,
                consumerSpanForTopic = tracing.tracer().nextSpan(extracted)
                    .kind(Span.Kind.CONSUMER)
                    .tag(KafkaTags.KAFKA_TOPIC_TAG, topic)
                    .start());
          }
          // no need to remove propagation headers as we failed to extract anything
          injector.inject(consumerSpanForTopic.context(), record.headers());
        } else { // we extracted request-scoped data, so cannot share a consumer span.
          Span span = tracing.tracer().nextSpan(extracted).kind(Span.Kind.CONSUMER)
              .tag(KafkaTags.KAFKA_TOPIC_TAG, topic)
              .start();
          span.finish();  // span won't be shared by other records
          // remove prior propagation headers from the record
          tracing.propagation().keys().forEach(key -> record.headers().remove(key));
          injector.inject(span.context(), record.headers());
        }
      }
    }
    consumerSpansForTopic.values().forEach(Span::finish);
    return records;
  }

  @Override public Set<TopicPartition> assignment() {
    return delegate.assignment();
  }

  @Override public Set<String> subscription() {
    return delegate.subscription();
  }

  @Override public void subscribe(Collection<String> topics) {
    delegate.subscribe(topics);
  }

  @Override public void subscribe(Collection<String> topics, ConsumerRebalanceListener callback) {
    delegate.subscribe(topics, callback);
  }

  @Override public void assign(Collection<TopicPartition> partitions) {
    delegate.assign(partitions);
  }

  @Override public void subscribe(Pattern pattern, ConsumerRebalanceListener callback) {
    delegate.subscribe(pattern, callback);
  }

  @Override public void unsubscribe() {
    delegate.unsubscribe();
  }

  @Override public void commitSync() {
    delegate.commitSync();
  }

  @Override public void commitSync(Map<TopicPartition, OffsetAndMetadata> offsets) {
    delegate.commitSync(offsets);
  }

  @Override public void commitAsync() {
    delegate.commitAsync();
  }

  @Override public void commitAsync(OffsetCommitCallback callback) {
    delegate.commitAsync(callback);
  }

  @Override public void commitAsync(Map<TopicPartition, OffsetAndMetadata> offsets,
      OffsetCommitCallback callback) {
    delegate.commitAsync(offsets, callback);
  }

  @Override public void seek(TopicPartition partition, long offset) {
    delegate.seek(partition, offset);
  }

  @Override public void seekToBeginning(Collection<TopicPartition> partitions) {
    delegate.seekToBeginning(partitions);
  }

  @Override public void seekToEnd(Collection<TopicPartition> partitions) {
    delegate.seekToEnd(partitions);
  }

  @Override public long position(TopicPartition partition) {
    return delegate.position(partition);
  }

  @Override public OffsetAndMetadata committed(TopicPartition partition) {
    return delegate.committed(partition);
  }

  @Override public Map<MetricName, ? extends Metric> metrics() {
    return delegate.metrics();
  }

  @Override public List<PartitionInfo> partitionsFor(String topic) {
    return delegate.partitionsFor(topic);
  }

  @Override public Map<String, List<PartitionInfo>> listTopics() {
    return delegate.listTopics();
  }

  @Override public Set<TopicPartition> paused() {
    return delegate.paused();
  }

  @Override public void pause(Collection<TopicPartition> partitions) {
    delegate.pause(partitions);
  }

  @Override public void resume(Collection<TopicPartition> partitions) {
    delegate.resume(partitions);
  }

  @Override public Map<TopicPartition, OffsetAndTimestamp> offsetsForTimes(
      Map<TopicPartition, Long> timestampsToSearch) {
    return delegate.offsetsForTimes(timestampsToSearch);
  }

  @Override
  public Map<TopicPartition, Long> beginningOffsets(Collection<TopicPartition> partitions) {
    return delegate.beginningOffsets(partitions);
  }

  @Override public Map<TopicPartition, Long> endOffsets(Collection<TopicPartition> partitions) {
    return delegate.endOffsets(partitions);
  }

  @Override public void close() {
    delegate.close();
  }

  @Override public void close(long timeout, TimeUnit unit) {
    delegate.close(timeout, unit);
  }

  @Override public void wakeup() {
    delegate.wakeup();
  }
}
