/*
 * Copyright 2013-2020 The OpenZipkin Authors
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

import brave.Tracing;
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig;
import brave.handler.MutableSpan;
import brave.propagation.B3Propagation;
import brave.propagation.Propagation;
import brave.propagation.StrictCurrentTraceContext;
import brave.propagation.TraceContext;
import brave.test.TestSpanHandler;
import com.google.common.base.Charsets;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.junit.After;

import static brave.test.ITRemote.BAGGAGE_FIELD;
import static brave.test.ITRemote.BAGGAGE_FIELD_KEY;
import static org.assertj.core.api.Assertions.assertThat;

public class KafkaTest {
  static final String TEST_TOPIC = "myTopic";
  static final String TEST_KEY = "foo";
  static final String TEST_VALUE = "bar";

  StrictCurrentTraceContext currentTraceContext = StrictCurrentTraceContext.create();
  TestSpanHandler spans = new TestSpanHandler();
  Tracing tracing = Tracing.newBuilder()
      .currentTraceContext(currentTraceContext)
      .addSpanHandler(spans)
      .propagationFactory(BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
          .add(BaggagePropagationConfig.SingleBaggageField.newBuilder(BAGGAGE_FIELD)
              .addKeyName(BAGGAGE_FIELD_KEY)
              .build()).build())
      .build();

  KafkaTracing kafkaTracing = KafkaTracing.create(tracing);
  TraceContext parent = tracing.tracer().newTrace().context();
  TraceContext incoming = tracing.tracer().newTrace().context();

  ConsumerRecord<String, String>
      consumerRecord = new ConsumerRecord<>(TEST_TOPIC, 0, 1L, TEST_KEY, TEST_VALUE);
  ProducerRecord<Object, String>
      producerRecord = new ProducerRecord<>(TEST_TOPIC, TEST_KEY, TEST_VALUE);
  RuntimeException error = new RuntimeException("Test exception");

  @After public void close() {
    tracing.close();
    currentTraceContext.close();
  }

  RecordMetadata createRecordMetadata() {
    TopicPartition tp = new TopicPartition("foo", 0);
    long timestamp = 2340234L;
    int keySize = 3;
    int valueSize = 5;
    Long checksum = 908923L;
    return new RecordMetadata(tp, -1L, -1L, timestamp, checksum, keySize, valueSize);
  }

  static void assertChildOf(MutableSpan child, TraceContext parent) {
    assertThat(child.parentId())
        .isEqualTo(parent.spanIdString());
  }

  static <K, V> void addB3MultiHeaders(TraceContext parent, ConsumerRecord<K, V> record) {
    Propagation.B3_STRING.injector(KafkaPropagation.SETTER).inject(parent, record.headers());
  }

  static Set<Entry<String, String>> lastHeaders(Headers headers) {
    Map<String, String> result = new LinkedHashMap<>();
    headers.forEach(h -> result.put(h.key(), new String(h.value(), Charsets.UTF_8)));
    return result.entrySet();
  }

  static Map<String, String> lastHeaders(MockProducer<Object, String> mockProducer) {
    Map<String, String> headers = new LinkedHashMap<>();
    List<ProducerRecord<Object, String>> history = mockProducer.history();
    ProducerRecord<Object, String> lastRecord = history.get(history.size() - 1);
    for (Header header : lastRecord.headers()) {
      headers.put(header.key(), new String(header.value(), StandardCharsets.UTF_8));
    }
    return headers;
  }
}
