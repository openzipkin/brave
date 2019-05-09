package brave.kafka.clients;

import brave.Span;
import brave.Tracing;
import brave.messaging.MessagingAdapter;
import brave.messaging.MessagingConsumerHandler;
import brave.messaging.MessagingProducerHandler;
import brave.messaging.MessagingTracing;
import brave.propagation.B3SingleFormat;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import java.util.Iterator;
import java.util.List;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import static brave.kafka.clients.KafkaPropagation.B3_SINGLE_TEST_HEADERS;
import static brave.kafka.clients.KafkaPropagation.TEST_CONTEXT;

/** Use this class to decorate your Kafka consumer / producer and enable Tracing. */
public final class KafkaTracing {

  static final String PROTOCOL = "kafka";
  static final String PRODUCER_OPERATION = "send";
  static final String CONSUMER_OPERATION = "poll";

  public static KafkaTracing create(Tracing tracing) {
    return new Builder(tracing).build();
  }

  public static Builder newBuilder(Tracing tracing) {
    return new Builder(tracing);
  }

  public static final class Builder {
    final MessagingTracing msgTracing;
    String remoteServiceName = "kafka";
    boolean writeB3SingleFormat;

    Builder(Tracing tracing) {
      if (tracing == null) throw new NullPointerException("tracing == null");
      this.msgTracing = MessagingTracing.create(tracing);
    }

    Builder(MessagingTracing msgTracing) {
      if (msgTracing == null) throw new NullPointerException("msgTracing == null");
      this.msgTracing = msgTracing;
    }

    /**
     * The remote service name that describes the broker in the dependency graph. Defaults to
     * "kafka"
     */
    public Builder remoteServiceName(String remoteServiceName) {
      this.remoteServiceName = remoteServiceName;
      return this;
    }

    /**
     * When true, only writes a single {@link B3SingleFormat b3 header} for outbound propagation.
     *
     * <p>Use this to reduce overhead. Note: normal {@link Tracing#propagation()} is used to parse
     * incoming headers. The implementation must be able to read "b3" headers.
     */
    public Builder writeB3SingleFormat(boolean writeB3SingleFormat) {
      this.writeB3SingleFormat = writeB3SingleFormat;
      return this;
    }

    public KafkaTracing build() {
      return new KafkaTracing(this);
    }
  }

  final MessagingTracing msgTracing;
  final String remoteServiceName;
  final List<String> propagationKeys;
  final Extractor<ConsumerRecord> consumerRecordExtractor;
  final Injector<ConsumerRecord> consumerRecordInjector;
  final MessagingConsumerHandler<ConsumerRecord> consumerHandler;
  final Extractor<ProducerRecord> producerRecordExtractor;
  final Injector<ProducerRecord> producerRecordInjector;
  final MessagingProducerHandler<ProducerRecord> producerHandler;

  KafkaTracing(Builder builder) { // intentionally hidden constructor
    this.msgTracing = builder.msgTracing;
    this.remoteServiceName = builder.remoteServiceName;
    this.propagationKeys = msgTracing.tracing().propagation().keys();
    final Extractor<Headers> extractor = msgTracing.tracing().propagation().extractor(KafkaPropagation.HEADERS_GETTER);
    List<String> keyList = msgTracing.tracing().propagation().keys();
    boolean singleFormat = false;
    if (builder.writeB3SingleFormat || keyList.equals(Propagation.B3_SINGLE_STRING.keys())) {
      TraceContext testExtraction = extractor.extract(B3_SINGLE_TEST_HEADERS).context();
      if (!TEST_CONTEXT.equals(testExtraction)) {
        throw new IllegalArgumentException(
            "KafkaTracing.Builder.writeB3SingleFormat set, but Tracing.Builder.propagationFactory cannot parse this format!");
      }
      singleFormat = true;
    }
    this.producerRecordInjector = singleFormat ? KafkaPropagation.B3_SINGLE_INJECTOR_PRODUCER
        : msgTracing.tracing().propagation().injector(KafkaPropagation.PRODUCER_RECORD_SETTER);
    this.producerRecordExtractor =
        msgTracing.tracing().propagation().extractor(KafkaPropagation.PRODUCER_RECORD_GETTER);
    this.producerHandler =
        MessagingProducerHandler.create(msgTracing,
            TracingProducer.KafkaProducerAdapter.create(this),
            producerRecordExtractor, producerRecordInjector);
    this.consumerRecordInjector = singleFormat ? KafkaPropagation.B3_SINGLE_INJECTOR_CONSUMER
        : msgTracing.tracing()
            .propagation()
            .injector(KafkaPropagation.CONSUMER_RECORD_SETTER);
    this.consumerRecordExtractor =
        msgTracing.tracing().propagation().extractor(KafkaPropagation.CONSUMER_RECORD_GETTER);
    this.consumerHandler =
        MessagingConsumerHandler.create(msgTracing,
            TracingConsumer.KafkaConsumerAdapter.create(this),
            consumerRecordExtractor, consumerRecordInjector);

  }

  /**
   * Extracts or creates a {@link Span.Kind#CONSUMER} span for each message received. This span is
   * injected onto each message so it becomes the parent when a processor later calls {@link
   * #nextSpan(ConsumerRecord)}.
   */
  public <K, V> Consumer<K, V> consumer(Consumer<K, V> consumer) {
    return new TracingConsumer<>(consumer, this);
  }

  /** Starts and propagates {@link Span.Kind#PRODUCER} span for each message sent. */
  public <K, V> Producer<K, V> producer(Producer<K, V> producer) {
    return new TracingProducer<>(producer, this);
  }

  /**
   * Use this to create a span for processing the given record. Note: the result has no name and is
   * not started.
   *
   * <p>This creates a child from identifiers extracted from the record headers, or a new span if
   * one couldn't be extracted.
   */
  public Span nextSpan(ConsumerRecord<?, ?> record) {
    return consumerHandler.nextSpan(record);
  }


  static abstract class KafkaAdapter<Record> extends MessagingAdapter<Record> {
    final String baseRemoteServiceName;
    final List<String> propagationKeys;

    KafkaAdapter(String baseRemoteServiceName,
        List<String> propagationKeys) {
      this.baseRemoteServiceName = baseRemoteServiceName;
      this.propagationKeys = propagationKeys;
    }

    @Override public String channelTagKey(Record record) {
      return String.format("%s.topic", PROTOCOL);
    }

    String recordKey(Object key) {
      if (key instanceof String && !"".equals(key)) {
        return key.toString();
      }
      return null;
    }

    @Override public String identifierTagKey() {
      return String.format("%s.key", PROTOCOL);
    }

    // BRAVE6: consider a messaging variant of extraction which clears headers as they are read.
    // this could prevent having to go back and clear them later. Another option is to encourage,
    // then special-case single header propagation. When there's only 1 propagation key, you don't
    // need to do a loop!
    void clearHeaders(Headers headers) {
      // Headers::remove creates and consumes an iterator each time. This does one loop instead.
      for (Iterator<Header> i = headers.iterator(); i.hasNext(); ) {
        Header next = i.next();
        if (propagationKeys.contains(next.key())) i.remove();
      }
    }
  }
}
