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

import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import com.google.common.base.Charsets;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.junit.After;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

abstract class BaseTracingTest {
  static String TRACE_ID = "463ac35c9f6413ad";
  static String PARENT_ID = "463ac35c9f6413ab";
  static String SPAN_ID = "48485a3953bb6124";
  static String SAMPLED = "1";

  String TEST_TOPIC = "myTopic";
  String TEST_KEY = "foo";
  String TEST_VALUE = "bar";

  ConsumerRecord<String, String> fakeRecord =
    new ConsumerRecord<>(TEST_TOPIC, 0, 1L, TEST_KEY, TEST_VALUE);

  BlockingQueue<Span> spans = new LinkedBlockingQueue<>();
  Tracing tracing = Tracing.newBuilder()
    .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
      .addScopeDecorator(StrictScopeDecorator.create())
      .build())
    .spanReporter(spans::add)
    .build();
  CurrentTraceContext current = tracing.currentTraceContext();
  KafkaTracing kafkaTracing = KafkaTracing.create(tracing);

  @After public void tearDown() {
    tracing.close();
  }

  static <K, V> void addB3MultiHeaders(ConsumerRecord<K, V> record) {
    record.headers()
      .add("X-B3-TraceId", TRACE_ID.getBytes(StandardCharsets.UTF_8))
      .add("X-B3-ParentSpanId", PARENT_ID.getBytes(StandardCharsets.UTF_8))
      .add("X-B3-SpanId", SPAN_ID.getBytes(StandardCharsets.UTF_8))
      .add("X-B3-Sampled", SAMPLED.getBytes(StandardCharsets.UTF_8));
  }

  static Set<Map.Entry<String, String>> lastHeaders(Headers headers) {
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

  /** Call this to block until a span was reported */
  static Span takeSpan(BlockingQueue<Span> spans) throws InterruptedException {
    Span result = spans.poll(3, TimeUnit.SECONDS);
    assertThat(result)
      .withFailMessage("span was not reported")
      .isNotNull();
    // ensure the span finished
    assertThat(result.durationAsLong()).isPositive();
    return result;
  }
}
