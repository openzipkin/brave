package brave.kafka.streams;

import brave.Span;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.TransformerSupplier;
import org.apache.kafka.streams.kstream.ValueTransformer;
import org.apache.kafka.streams.kstream.ValueTransformerSupplier;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.kstream.ValueTransformerWithKeySupplier;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class KafkaStreamsTracingTest extends BaseTracingTest {

  @Test
  public void kafkaClientSupplier_should_return_producer() {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("bootstrap.servers", "localhost:9092");
    assertThat(kafkaStreamsTracing.kafkaClientSupplier().getProducer(config)).isNotNull();
  }

  @Test
  public void kafkaClientSupplier_should_return_consumer() {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("bootstrap.servers", "localhost:9092");
    assertThat(kafkaStreamsTracing.kafkaClientSupplier().getConsumer(config)).isNotNull();
  }

  @Test
  public void nextSpan_uses_current_context() {
    ProcessorContext fakeProcessorContext = processorContextSupplier.apply(new RecordHeaders());
    Span child;
    try (CurrentTraceContext.Scope ws = tracing.currentTraceContext()
        .newScope(TraceContext.newBuilder().traceId(1).spanId(1).build())) {
      child = kafkaStreamsTracing.nextSpan(fakeProcessorContext);
    }
    assertThat(child.context().parentId())
        .isEqualTo(1L);
  }

  @Test
  public void nextSpan_should_create_span_if_no_headers() {
    ProcessorContext fakeProcessorContext = processorContextSupplier.apply(new RecordHeaders());
    assertThat(kafkaStreamsTracing.nextSpan(fakeProcessorContext)).isNotNull();
  }

  @Test
  public void nextSpan_should_tag_app_id_and_task_id() {
    ProcessorContext fakeProcessorContext = processorContextSupplier.apply(new RecordHeaders());
    kafkaStreamsTracing.nextSpan(fakeProcessorContext).start().finish();

    assertThat(spans)
        .flatExtracting(s -> s.tags().entrySet())
        .containsOnly(
            entry("kafka.streams.application.id", TEST_APPLICATION_ID),
            entry("kafka.streams.task.id", TEST_TASK_ID));
  }

  @Test
  public void processorSupplier_should_tag_app_id_and_task_id() {
    Processor<String, String> processor = fakeProcessorSupplier.get();
    processor.init(processorContextSupplier.apply(new RecordHeaders()));
    processor.process(TEST_KEY, TEST_VALUE);

    assertThat(spans)
        .flatExtracting(s -> s.tags().entrySet())
        .containsOnly(
            entry("kafka.streams.application.id", TEST_APPLICATION_ID),
            entry("kafka.streams.task.id", TEST_TASK_ID));
  }

  @Test
  public void transformSupplier_should_tag_app_id_and_task_id() {
    Transformer<String, String, KeyValue<String, String>> processor = fakeTransformerSupplier.get();
    processor.init(processorContextSupplier.apply(new RecordHeaders()));
    processor.transform(TEST_KEY, TEST_VALUE);

    assertThat(spans)
        .flatExtracting(s -> s.tags().entrySet())
        .containsOnly(
            entry("kafka.streams.application.id", TEST_APPLICATION_ID),
            entry("kafka.streams.task.id", TEST_TASK_ID));
  }

  @Test
  public void valueTransformSupplier_should_tag_app_id_and_task_id() {
    ValueTransformer<String, String> processor = fakeValueTransformerSupplier.get();
    processor.init(processorContextSupplier.apply(new RecordHeaders()));
    processor.transform(TEST_VALUE);

    assertThat(spans)
        .flatExtracting(s -> s.tags().entrySet())
        .containsOnly(
            entry("kafka.streams.application.id", TEST_APPLICATION_ID),
            entry("kafka.streams.task.id", TEST_TASK_ID));
  }

  @Test
  public void valueTransformWithKeySupplier_should_tag_app_id_and_task_id() {
    ValueTransformerWithKey<String, String, String> processor =
        fakeValueTransformerWithKeySupplier.get();
    processor.init(processorContextSupplier.apply(new RecordHeaders()));
    processor.transform(TEST_KEY, TEST_VALUE);

    assertThat(spans)
        .flatExtracting(s -> s.tags().entrySet())
        .containsOnly(
            entry("kafka.streams.application.id", TEST_APPLICATION_ID),
            entry("kafka.streams.task.id", TEST_TASK_ID));
  }
}
