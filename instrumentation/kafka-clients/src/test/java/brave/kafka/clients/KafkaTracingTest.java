package brave.kafka.clients;

import brave.Span;
import brave.Tracing;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import zipkin.internal.Util;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class KafkaTracingTest {
  String TRACE_ID = "463ac35c9f6413ad";
  String PARENT_ID = "463ac35c9f6413ab";
  String SPAN_ID = "48485a3953bb6124";
  String SAMPLED = "1";

  String TEST_TOPIC = "myTopic";
  String TEST_KEY = "foo";
  String TEST_VALUE = "bar";

  KafkaTracing kafkaTracing;
  ConsumerRecord<String, String> fakeRecord;

  List<zipkin2.Span> spans = new ArrayList<>();

  @Before
  public void setUp() throws IOException {
    Tracing tracing = Tracing.newBuilder()
        .spanReporter(spans::add)
        .sampler(Sampler.NEVER_SAMPLE)
        .build();

    kafkaTracing = KafkaTracing.create(tracing);
    fakeRecord = new ConsumerRecord<>(TEST_TOPIC, 0, 1, TEST_KEY, TEST_VALUE);
  }

  @After
  public void tearDown() throws Exception {
    spans.clear();
  }

  @Test
  public void should_retrieve_span_from_headers() throws Exception {
    addB3Headers();

    Span span = kafkaTracing.joinSpan(fakeRecord);

    TraceContext context = span.context();
    assertThat(Long.toHexString(context.traceId())).isEqualTo(TRACE_ID);
    assertThat(Long.toHexString(context.spanId())).isEqualTo(SPAN_ID);
    assertThat(context.sampled()).isEqualTo(true);
  }

  @Test
  public void should_create_span_if_no_headers() throws Exception {
    Span span = kafkaTracing.joinSpan(fakeRecord);

    TraceContext context = span.context();
    assertThat(Long.toHexString(context.traceId())).isNotEmpty().isNotEqualTo(TRACE_ID);
    assertThat(Long.toHexString(context.spanId())).isNotEmpty().isNotEqualTo(SPAN_ID);
    assertThat(context.sampled()).isEqualTo(false);
  }

  void addB3Headers() {
    fakeRecord.headers()
        .add("X-B3-TraceId", TRACE_ID.getBytes(Util.UTF_8))
        .add("X-B3-ParentSpanId", PARENT_ID.getBytes(Util.UTF_8))
        .add("X-B3-SpanId", SPAN_ID.getBytes(Util.UTF_8))
        .add("X-B3-Sampled", SAMPLED.getBytes(Util.UTF_8));
  }
}