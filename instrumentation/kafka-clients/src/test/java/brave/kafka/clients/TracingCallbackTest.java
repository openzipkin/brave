package brave.kafka.clients;

import brave.Span;
import brave.Tracing;
import brave.sampler.Sampler;
import java.util.LinkedList;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TracingCallbackTest {

  LinkedList<zipkin2.Span> spans = new LinkedList<>();

  Tracing tracing = Tracing.newBuilder()
      .spanReporter(spans::add)
      .sampler(Sampler.ALWAYS_SAMPLE)
      .build();

  @After
  public void close() throws Exception {
    Tracing current = Tracing.current();
    if (current != null) current.close();
    spans.clear();
  }

  @Test
  public void on_completion_should_finish_span() throws Exception {
    Span span = tracing.tracer().nextSpan();
    span.start();
    TracingCallback callback = new TracingCallback(span, null);
    callback.onCompletion(createRecordMetadata(), null);

    assertThat(spans.getFirst()).isNotNull();
  }

  @Test
  public void on_completion_should_tag_if_exception() throws Exception {
    Span span = tracing.tracer().nextSpan();
    span.start();
    TracingCallback callback = new TracingCallback(span, null);
    callback.onCompletion(null, new Exception("Test exception"));

    assertThat(spans.getFirst().tags())
        .containsKey("error");

    assertThat(spans.getFirst().tags())
        .containsEntry("error", "Test exception");
  }

  RecordMetadata createRecordMetadata() {
    TopicPartition tp = new TopicPartition("foo", 0);
    long timestamp = 2340234L;
    int keySize = 3;
    int valueSize = 5;
    Long checksum = 908923L;

    return new RecordMetadata(tp, -1L, -1L, timestamp, checksum, keySize, valueSize);
  }
}