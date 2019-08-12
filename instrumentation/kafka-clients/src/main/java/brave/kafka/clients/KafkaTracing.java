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
import brave.propagation.B3SingleFormat;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import static brave.kafka.clients.KafkaPropagation.B3_SINGLE_TEST_HEADERS;
import static brave.kafka.clients.KafkaPropagation.TEST_CONTEXT;

/** Use this class to decorate your Kafka consumer / producer and enable Tracing. */
public final class KafkaTracing {

  public static KafkaTracing create(Tracing tracing) {
    return new Builder(tracing).build();
  }

  public static Builder newBuilder(Tracing tracing) {
    return new Builder(tracing);
  }

  public static final class Builder {
    final Tracing tracing;
    String remoteServiceName = "kafka";
    boolean writeB3SingleFormat;

    Builder(Tracing tracing) {
      if (tracing == null) throw new NullPointerException("tracing == null");
      this.tracing = tracing;
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

  final Tracing tracing;
  final Extractor<Headers> extractor;
  final Injector<Headers> injector;
  final Set<String> propagationKeys;
  final String remoteServiceName;

  KafkaTracing(Builder builder) { // intentionally hidden constructor
    this.tracing = builder.tracing;
    this.extractor = tracing.propagation().extractor(KafkaPropagation.GETTER);
    List<String> keyList = builder.tracing.propagation().keys();
    // Use a more efficient injector if we are only propagating a single header
    if (builder.writeB3SingleFormat || keyList.equals(Propagation.B3_SINGLE_STRING.keys())) {
      TraceContext testExtraction = extractor.extract(B3_SINGLE_TEST_HEADERS).context();
      if (!TEST_CONTEXT.equals(testExtraction)) {
        throw new IllegalArgumentException(
          "KafkaTracing.Builder.writeB3SingleFormat set, but Tracing.Builder.propagationFactory cannot parse this format!");
      }
      this.injector = KafkaPropagation.B3_SINGLE_INJECTOR;
    } else {
      this.injector = tracing.propagation().injector(KafkaPropagation.SETTER);
    }
    this.propagationKeys = new LinkedHashSet<>(keyList);
    this.remoteServiceName = builder.remoteServiceName;
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
    TraceContextOrSamplingFlags extracted = extractAndClearHeaders(record.headers());
    Span result = tracing.tracer().nextSpan(extracted);
    if (extracted.context() == null && !result.isNoop()) {
      addTags(record, result);
    }
    return result;
  }

  TraceContextOrSamplingFlags extractAndClearHeaders(Headers headers) {
    TraceContextOrSamplingFlags extracted = extractor.extract(headers);
    // clear propagation headers if we were able to extract a span
    if (!extracted.equals(TraceContextOrSamplingFlags.EMPTY)) {
      clearHeaders(headers);
    }
    return extracted;
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

  /** When an upstream context was not present, lookup keys are unlikely added */
  static void addTags(ConsumerRecord<?, ?> record, SpanCustomizer result) {
    if (record.key() instanceof String && !"".equals(record.key())) {
      result.tag(KafkaTags.KAFKA_KEY_TAG, record.key().toString());
    }
    result.tag(KafkaTags.KAFKA_TOPIC_TAG, record.topic());
  }
}
