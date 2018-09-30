package brave.kafka.streams;

import brave.Span;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.TransformerSupplier;
import org.apache.kafka.streams.processor.AbstractProcessor;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.ProcessorSupplier;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

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
  public void processorSupplier_should_tag_key_if_string() {
    Processor<String, String> processor = fakeProcessorSupplier.get();
    processor.init(processorContextSupplier.apply(new RecordHeaders()));
    processor.process(TEST_KEY, TEST_VALUE);

    assertThat(spans)
            .flatExtracting(s -> s.tags().entrySet())
            .containsOnly(
                    entry("kafka.streams.application.id", TEST_APPLICATION_ID),
                    entry("kafka.streams.task.id", TEST_TASK_ID),
                    entry("kafka.streams.key", TEST_KEY));
  }

  @Test
  public void processorSupplier_should_tag_key_if_null() {
    Processor<String, String> processor = fakeProcessorSupplier.get();
    processor.init(processorContextSupplier.apply(new RecordHeaders()));
    processor.process(null, TEST_VALUE);

    assertThat(spans)
            .flatExtracting(s -> s.tags().entrySet())
            .containsOnly(
                    entry("kafka.streams.application.id", TEST_APPLICATION_ID),
                    entry("kafka.streams.task.id", TEST_TASK_ID));
  }

  @Test
  public void processorSupplier_should_tag_key_if_binary() {
    ProcessorSupplier<byte[], String> fakeProcessorSupplier =
            kafkaStreamsTracing.processorSupplier(
                    "processor-1",
                    new AbstractProcessor<byte[], String>() {
                      @Override
                      public void process(byte[] key, String value) {
                        context().forward(key, value);
                      }
                    });

    Processor<byte[], String> processor = fakeProcessorSupplier.get();
    processor.init(processorContextSupplier.apply(new RecordHeaders()));
    processor.process(new byte[1], TEST_VALUE);

    assertThat(spans)
            .flatExtracting(s -> s.tags().entrySet())
            .containsOnly(
                    entry("kafka.streams.application.id", TEST_APPLICATION_ID),
                    entry("kafka.streams.task.id", TEST_TASK_ID));
  }

  @Test
  public void transformSupplier_should_tag_key_if_string() {
    Transformer processor = fakeTransformerSupplier.get();
    processor.init(processorContextSupplier.apply(new RecordHeaders()));
    processor.transform(TEST_KEY, TEST_VALUE);

    assertThat(spans)
            .flatExtracting(s -> s.tags().entrySet())
            .containsOnly(
                    entry("kafka.streams.application.id", TEST_APPLICATION_ID),
                    entry("kafka.streams.task.id", TEST_TASK_ID),
                    entry("kafka.streams.key", TEST_KEY));
  }

  @Test
  public void transformSupplier_should_not_tag_key_if_null() {
    Transformer processor = fakeTransformerSupplier.get();
    processor.init(processorContextSupplier.apply(new RecordHeaders()));
    processor.transform(null, TEST_VALUE);

    assertThat(spans)
            .flatExtracting(s -> s.tags().entrySet())
            .containsOnly(
                    entry("kafka.streams.application.id", TEST_APPLICATION_ID),
                    entry("kafka.streams.task.id", TEST_TASK_ID));
  }

  @Test
  public void transformSupplier_should_not_tag_key_if_binary() {
    TransformerSupplier<byte[], String, String> fakeTransformerSupplier =
            kafkaStreamsTracing.transformerSupplier(
                    "transformer-1",
                    new Transformer<byte[], String, String>() {
                      ProcessorContext context;
                      @Override
                      public void init(ProcessorContext context) {
                        this.context = context;
                      }

                      @Override
                      public String transform(byte[] key, String value) {
                        return "transformed";
                      }

                      @Override
                      public void close() {
                      }
                    });

    Transformer processor = fakeTransformerSupplier.get();
    processor.init(processorContextSupplier.apply(new RecordHeaders()));
    processor.transform(new byte[1], TEST_VALUE);

    assertThat(spans)
            .flatExtracting(s -> s.tags().entrySet())
            .containsOnly(
                    entry("kafka.streams.application.id", TEST_APPLICATION_ID),
                    entry("kafka.streams.task.id", TEST_TASK_ID));
  }
}
