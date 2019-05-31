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
import brave.SpanCustomizer;
import brave.Tracing;
import brave.internal.Nullable;
import brave.kafka.clients.TracingConsumer.ConsumerRecordAdapter;
import brave.kafka.clients.TracingProducer.ProducerRecordAdapter;
import brave.messaging.ConsumerHandler;
import brave.messaging.MessagingAdapter;
import brave.messaging.MessagingParser;
import brave.messaging.MessagingTracing;
import brave.messaging.ProcessorHandler;
import brave.messaging.ProducerHandler;
import brave.propagation.B3SingleFormat;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import java.util.List;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;

import static brave.kafka.clients.KafkaPropagation.B3_SINGLE_TEST_HEADERS;
import static brave.kafka.clients.KafkaPropagation.HEADERS_GETTER;
import static brave.kafka.clients.KafkaPropagation.HEADERS_SETTER;
import static brave.kafka.clients.KafkaPropagation.TEST_CONTEXT;
import static brave.propagation.B3SingleFormat.writeB3SingleFormatWithoutParentIdAsBytes;

/** Use this class to decorate your Kafka consumer / producer and enable Tracing. */
public final class KafkaTracing {
  public static KafkaTracing create(Tracing tracing) {
    return new Builder(tracing).build();
  }

  public static Builder newBuilder(Tracing tracing) {
    return new Builder(tracing);
  }

  public static final class Builder {
    final MessagingTracing messagingTracing;
    String remoteServiceName = "kafka";
    boolean writeB3SingleFormat;

    Builder(Tracing tracing) {
      if (tracing == null) throw new NullPointerException("tracing == null");
      this.messagingTracing =
        MessagingTracing.newBuilder(tracing).parser(new LegacyMessagingParser()).build();
    }

    Builder(MessagingTracing messagingTracing) {
      if (messagingTracing == null) throw new NullPointerException("messagingTracing == null");
      this.messagingTracing = messagingTracing;
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

  final MessagingTracing messagingTracing;
  final ConsumerHandler<String, ConsumerRecord, Headers> consumerHandler;
  final ProcessorHandler<String, ConsumerRecord, Headers> processorHandler;
  final ProducerHandler<String, ProducerRecord, Headers> producerHandler;

  KafkaTracing(Builder builder) { // intentionally hidden constructor
    this.messagingTracing = builder.messagingTracing;
    Extractor<Headers> extractor =
      messagingTracing.tracing().propagation().extractor(HEADERS_GETTER);
    Injector<Headers> injector;
    List<String> keyList = messagingTracing.tracing().propagation().keys();
    if (builder.writeB3SingleFormat || keyList.equals(Propagation.B3_SINGLE_STRING.keys())) {
      TraceContext testExtraction = extractor.extract(B3_SINGLE_TEST_HEADERS).context();
      if (!TEST_CONTEXT.equals(testExtraction)) {
        throw new IllegalArgumentException(
          "KafkaTracing.Builder.writeB3SingleFormat set, but Tracing.Builder.propagationFactory cannot parse this format!");
      }
      injector = new Injector<Headers>() {
        @Override public void inject(TraceContext traceContext, Headers headers) {
          headers.add("b3", writeB3SingleFormatWithoutParentIdAsBytes(traceContext));
        }

        @Override public String toString() {
          return "Headers::add(\"b3\",singleHeaderFormatWithoutParent)";
        }
      };
    } else {
      injector = messagingTracing.tracing().propagation().injector(HEADERS_SETTER);
    }
    consumerHandler = ConsumerHandler.create(
      messagingTracing, new ConsumerRecordAdapter(builder.remoteServiceName), extractor, injector);
    processorHandler = ProcessorHandler.create(messagingTracing, consumerHandler);
    producerHandler = ProducerHandler.create(
      messagingTracing, new ProducerRecordAdapter(builder.remoteServiceName), extractor, injector);
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
  public <K, V> Span nextSpan(ConsumerRecord<K, V> record) {
    return processorHandler.startProcessor(record.topic(), record, false);
  }

  @Nullable static String recordKey(Object key) {
    if (key instanceof String && !"".equals(key)) {
      return key.toString();
    }
    return null;
  }

  static class LegacyMessagingParser extends MessagingParser {
    @Override
    protected <Chan, Msg, C> void addMessageTags(MessagingAdapter<Chan, Msg, C> adapter,
      Chan channel, @Nullable Msg msg, TraceContext context, SpanCustomizer customizer) {
      String channelName = adapter.channel(channel);
      if (channelName != null) customizer.tag("kafka.topic", channelName);
      if (msg != null && context.parentId() == null) { // is a root span
        String messageKey = adapter.messageKey(msg);
        if (messageKey != null) customizer.tag("kafka.key", messageKey);
      }
    }

    /** Returns the span name of a message operation. Defaults to the operation name. */
    @Override protected <Chan, Msg, C> String spanName(String operation,
      MessagingAdapter<Chan, Msg, C> adapter, Chan channel, @Nullable Msg msg) {
      switch (operation) {
        case "receive":
        case "receive-batch":
          return "poll";
      }
      return super.spanName(operation, adapter, channel, msg);
    }
  }
}
