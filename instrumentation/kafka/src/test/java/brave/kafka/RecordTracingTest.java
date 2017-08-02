package brave.kafka;

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

public class RecordTracingTest {
  private static final String TRACE_ID = "463ac35c9f6413ad";
  private static final String PARENT_ID = "463ac35c9f6413ab";
  private static final String SPAN_ID = "48485a3953bb6124";
  private static final String SAMPLED = "1";

  private static final String TEST_TOPIC = "myTopic";
  private static final String TEST_KEY = "foo";
  private static final String TEST_VALUE = "bar";

  private RecordTracing recordTracing;
  private ConsumerRecord<String, String> fakeRecord;

  private List<zipkin.Span> spans = new ArrayList<>();

  @Before
  public void setUp() throws IOException {
    Tracing tracing = Tracing.newBuilder()
        .reporter(spans::add)
        .sampler(Sampler.NEVER_SAMPLE)
        .build();

    recordTracing = new RecordTracing(tracing);
    fakeRecord = new ConsumerRecord<>(TEST_TOPIC, 0, 1, TEST_KEY, TEST_VALUE);
  }

  @After
  public void tearDown() throws Exception {
    spans.clear();
  }

  @Test
  public void should_create_child_from_headers() throws Exception {
    addB3Headers();

    Span span = recordTracing.nexSpanFromRecord(fakeRecord);

    TraceContext context = span.context();
    assertThat(Long.toHexString(context.traceId())).isEqualTo(TRACE_ID);
    assertThat(Long.toHexString(context.parentId())).isEqualTo(SPAN_ID);
    assertThat(context.sampled()).isEqualTo(true);
  }

  @Test
  public void should_create_new_span_from_headers() throws Exception {
    Span span = recordTracing.nexSpanFromRecord(fakeRecord);

    TraceContext context = span.context();
    assertThat(Long.toHexString(context.traceId())).isNotEqualTo(TRACE_ID);
    assertThat(context.parentId()).isNull();
    assertThat(context.sampled()).isEqualTo(false);
  }

  @Test
  public void should_finish_span_from_headers() throws Exception {
    addB3Headers();
    recordTracing.finishProducerSpan(fakeRecord);

    assertThat(spans)
        .extracting(s -> Long.toHexString(s.id))
        .containsExactly(SPAN_ID);

    assertThat(spans)
        .flatExtracting(s -> s.annotations)
        .extracting(a -> a.value)
        .containsExactly("sr");
  }

  @Test
  public void should_do_nothing_if_b3_missing() throws Exception {
    recordTracing.finishProducerSpan(fakeRecord);

    assertThat(spans).isEmpty();
  }

  private void addB3Headers() {
    fakeRecord.headers()
        .add("X-B3-TraceId", TRACE_ID.getBytes(Util.UTF_8))
        .add("X-B3-ParentSpanId", PARENT_ID.getBytes(Util.UTF_8))
        .add("X-B3-SpanId", SPAN_ID.getBytes(Util.UTF_8))
        .add("X-B3-Sampled", SAMPLED.getBytes(Util.UTF_8));
  }
}