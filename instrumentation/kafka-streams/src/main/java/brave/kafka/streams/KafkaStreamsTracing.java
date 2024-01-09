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
package brave.kafka.streams;

import brave.Span;
import brave.SpanCustomizer;
import brave.Tracer;
import brave.Tracing;
import brave.kafka.clients.KafkaTracing;
import brave.messaging.MessagingTracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.streams.KafkaClientSupplier;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.api.FixedKeyProcessorSupplier;
import org.apache.kafka.streams.processor.api.ProcessingContext;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;

/** Use this class to decorate Kafka Stream Topologies and enable Tracing. */
public final class KafkaStreamsTracing {
  final KafkaTracing kafkaTracing;
  final Tracer tracer;
  final Extractor<Headers> extractor;
  final Injector<Headers> injector;
  final Set<String> propagationKeys;
  final TraceContextOrSamplingFlags emptyExtraction;

  KafkaStreamsTracing(Builder builder) { // intentionally hidden constructor
    this.kafkaTracing = builder.kafkaTracing.toBuilder()
      .singleRootSpanOnReceiveBatch(builder.singleRootSpanOnReceiveBatch)
      .build();
    this.tracer = kafkaTracing.messagingTracing().tracing().tracer();
    Propagation<String> propagation = kafkaTracing.messagingTracing().propagation();
    this.extractor = propagation.extractor(KafkaStreamsPropagation.GETTER);
    this.injector = propagation.injector(KafkaStreamsPropagation.SETTER);
    this.propagationKeys = new LinkedHashSet<>(propagation.keys());
    // When Baggage or similar are in use, the result != TraceContextOrSamplingFlags.EMPTY
    this.emptyExtraction = propagation.extractor((c, k) -> null).extract(Boolean.TRUE);
  }

  public static KafkaStreamsTracing create(Tracing tracing) {
    return create(KafkaTracing.create(tracing));
  }

  /** @since 5.10 */
  public static KafkaStreamsTracing create(MessagingTracing messagingTracing) {
    return new Builder(KafkaTracing.create(messagingTracing)).build();
  }

  /** @since 5.9 */
  public static KafkaStreamsTracing create(KafkaTracing kafkaTracing) {
    return new Builder(kafkaTracing).build();
  }

  /** @since 5.10 */
  public static Builder newBuilder(Tracing tracing) {
    return new Builder(KafkaTracing.create(tracing));
  }

  /** @since 5.10 */
  public static Builder newBuilder(MessagingTracing messagingTracing) {
    return new Builder(KafkaTracing.create(messagingTracing));
  }

  /**
   * Provides a {@link KafkaClientSupplier} with tracing enabled, hence Producer and Consumer
   * operations will be traced.
   * <p>
   * This is meant to be used in scenarios {@link KafkaStreams} creation is not controlled by the
   * user but framework (e.g. Spring Kafka Streams) creates it, and {@link KafkaClientSupplier} is
   * accepted.
   */
  public KafkaClientSupplier kafkaClientSupplier() {
    return new TracingKafkaClientSupplier(kafkaTracing);
  }

  /**
   * Creates a {@link KafkaStreams} instance with a tracing-enabled {@link KafkaClientSupplier}. All
   * Topology Sources and Sinks (including internal Topics) will create Spans on records processed
   * (i.e. send or consumed).
   * <p>
   * Use this instead of {@link KafkaStreams} constructor.
   *
   * <p>Simple example:
   * <pre>{@code
   * // KafkaStreams with tracing-enabled KafkaClientSupplier
   * KafkaStreams kafkaStreams = kafkaStreamsTracing.kafkaStreams(topology, streamsConfig);
   * }</pre>
   *
   * @see TracingKafkaClientSupplier
   */
  public KafkaStreams kafkaStreams(Topology topology, Properties streamsConfig) {
    return new KafkaStreams(topology, streamsConfig, kafkaClientSupplier());
  }

  /**
   * Create a tracing-decorated {@link ProcessorSupplier}
   *
   * <p>Simple example using Kafka Streams DSL:
   * <pre>{@code
   * StreamsBuilder builder = new StreamsBuilder();
   * builder.stream(inputTopic)
   *        .process(kafkaStreamsTracing.process("my-processor", myProcessorSupplier);
   * }</pre>
   *
   * @see TracingKafkaClientSupplier
   */
  public <KIn, VIn, KOut, VOut> ProcessorSupplier<KIn, VIn, KOut, VOut> process(String spanName,
    ProcessorSupplier<KIn, VIn, KOut, VOut> processorSupplier) {
    return new TracingProcessorSupplier<>(this, spanName, processorSupplier);
  }

  /**
   * Create a tracing-decorated {@link FixedKeyProcessorSupplier}
   *
   * <p>Simple example using Kafka Streams DSL:
   * <pre>{@code
   * StreamsBuilder builder = new StreamsBuilder();
   * builder.stream(inputTopic)
   *        .processValues(kafkaStreamsTracing.processValues("my-processor", myFixedKeyProcessorSupplier);
   * }</pre>
   *
   * @see TracingKafkaClientSupplier
   */
  public <KIn, VIn, VOut> FixedKeyProcessorSupplier<KIn, VIn, VOut> processValues(String spanName,
    FixedKeyProcessorSupplier<KIn, VIn, VOut> processorSupplier) {
    return new TracingFixedKeyProcessorSupplier<>(this, spanName, processorSupplier);
  }

  static void addTags(ProcessorContext processorContext, SpanCustomizer result) {
    result.tag(KafkaStreamsTags.KAFKA_STREAMS_APPLICATION_ID_TAG, processorContext.applicationId());
    result.tag(KafkaStreamsTags.KAFKA_STREAMS_TASK_ID_TAG, processorContext.taskId().toString());
  }

  static void addTags(ProcessingContext processingContext, SpanCustomizer result) {
    result.tag(KafkaStreamsTags.KAFKA_STREAMS_APPLICATION_ID_TAG,
      processingContext.applicationId());
    result.tag(KafkaStreamsTags.KAFKA_STREAMS_TASK_ID_TAG, processingContext.taskId().toString());
  }

  Span nextSpan(ProcessingContext context, Headers headers) {
    TraceContextOrSamplingFlags extracted = extractor.extract(headers);
    // Clear any propagation keys present in the headers
    if (!extracted.equals(emptyExtraction)) {
      clearHeaders(headers);
    }
    Span result = tracer.nextSpan(extracted);
    if (!result.isNoop()) {
      addTags(context, result);
    }
    return result;
  }

  // We can't just skip clearing headers we use because we might inject B3 single, yet have stale B3
  // multi, or vice versa.
  void clearHeaders(Headers headers) {
    // Headers::remove creates and consumes an iterator each time. This does one loop instead.
    for (Iterator<Header> i = headers.iterator(); i.hasNext(); ) {
      Header next = i.next();
      if (propagationKeys.contains(next.key())) i.remove();
    }
  }

  public static final class Builder {
    final KafkaTracing kafkaTracing;
    boolean singleRootSpanOnReceiveBatch = false;

    Builder(KafkaTracing kafkaTracing) {
      if (kafkaTracing == null) throw new NullPointerException("kafkaTracing == null");
      this.kafkaTracing = kafkaTracing;
    }

    /**
     * Controls the sharing of a {@code poll} span for incoming spans with no trace context.
     *
     * <p>If true, all the spans received in a poll batch that do not have trace-context will be
     * added to a single new {@code poll} root span. Otherwise, a {@code poll} span will be created
     * for each such message.
     *
     * @since 5.10
     */
    public Builder singleRootSpanOnReceiveBatch(boolean singleRootSpanOnReceiveBatch) {
      this.singleRootSpanOnReceiveBatch = singleRootSpanOnReceiveBatch;
      return this;
    }

    public KafkaStreamsTracing build() {
      return new KafkaStreamsTracing(this);
    }
  }
}
