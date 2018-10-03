package brave.kafka.streams;

import brave.Tracing;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Function;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.TransformerSupplier;
import org.apache.kafka.streams.processor.AbstractProcessor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.ProcessorSupplier;
import org.apache.kafka.streams.processor.TaskId;
import org.junit.After;
import zipkin2.Span;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

abstract class BaseTracingTest {
  String TEST_APPLICATION_ID = "myAppId";
  String TEST_TASK_ID = "0_0";
  String TEST_TOPIC = "myTopic";
  String TEST_KEY = "foo";
  String TEST_VALUE = "bar";


  Function<Headers, ProcessorContext> processorContextSupplier =
      (Headers headers) ->
      {
        ProcessorContext processorContext = mock(ProcessorContext.class);
        when(processorContext.applicationId()).thenReturn(TEST_APPLICATION_ID);
        when(processorContext.topic()).thenReturn(TEST_TOPIC);
        when(processorContext.taskId()).thenReturn(new TaskId(0, 0));
        when(processorContext.headers()).thenReturn(headers);
        return processorContext;
      };

  ConcurrentLinkedDeque<Span> spans = new ConcurrentLinkedDeque<>();
  Tracing tracing = Tracing.newBuilder()
      .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
          .addScopeDecorator(StrictScopeDecorator.create())
          .build())
      .spanReporter(spans::add)
      .build();
  KafkaStreamsTracing kafkaStreamsTracing = KafkaStreamsTracing.create(tracing);

  ProcessorSupplier<String, String> fakeProcessorSupplier =
      kafkaStreamsTracing.processor(
          "forward-1",
          new AbstractProcessor<String, String>() {
            @Override
            public void process(String key, String value) {
              context().forward(key, value);
            }
          });

  TransformerSupplier<String, String, KeyValue<String, String>> fakeTransformerSupplier =
      kafkaStreamsTracing.transformer(
          "transformer-1",
          new Transformer<String, String, KeyValue<String, String>>() {
            ProcessorContext context;

            @Override
            public void init(ProcessorContext context) {
              this.context = context;
            }

            @Override
            public KeyValue<String, String> transform(String key, String value) {
              return KeyValue.pair(key, value);
            }

            @Override
            public void close() {
            }
          });

  @After
  public void tearDown() {
    tracing.close();
  }
}
