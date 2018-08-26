package brave.kafka.clients;

import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import com.google.common.base.Charsets;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.junit.After;
import zipkin2.Span;

abstract class BaseTracingTest {
  static final Charset UTF_8 = Charset.forName("UTF-8");
  static String TRACE_ID = "463ac35c9f6413ad";
  static String PARENT_ID = "463ac35c9f6413ab";
  static String SPAN_ID = "48485a3953bb6124";
  static String SAMPLED = "1";

  String TEST_TOPIC = "myTopic";
  String TEST_KEY = "foo";
  String TEST_VALUE = "bar";

  ConsumerRecord<String, String> fakeRecord =
      new ConsumerRecord<>(TEST_TOPIC, 0, 1L, TEST_KEY, TEST_VALUE);

  ConcurrentLinkedDeque<Span> spans = new ConcurrentLinkedDeque<>();
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

  static <K, V> void addB3Headers(ConsumerRecord<K, V> record) {
    record.headers()
        .add("X-B3-TraceId", TRACE_ID.getBytes(UTF_8))
        .add("X-B3-ParentSpanId", PARENT_ID.getBytes(UTF_8))
        .add("X-B3-SpanId", SPAN_ID.getBytes(UTF_8))
        .add("X-B3-Sampled", SAMPLED.getBytes(UTF_8));
  }

  static Set<Map.Entry<String, String>> lastHeaders(Headers headers) {
    Map<String, String> result = new LinkedHashMap<>();
    headers.forEach(h -> result.put(h.key(), new String(h.value(), Charsets.UTF_8)));
    return result.entrySet();
  }
}
