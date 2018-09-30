package brave.kafka.streams;

import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsMetrics;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.TransformerSupplier;
import org.apache.kafka.streams.processor.*;
import org.junit.After;
import zipkin2.Span;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Function;

abstract class BaseTracingTest {
    String TEST_APPLICATION_ID = "myAppId";
    String TEST_TASK_ID = "0_0";
    String TEST_TOPIC = "myTopic";
    String TEST_KEY = "foo";
    String TEST_VALUE = "bar";

    Function<Headers, ProcessorContext> processorContextSupplier =
            (Headers headers) ->
                    new ProcessorContext() {

                        @Override
                        public String applicationId() {
                            return TEST_APPLICATION_ID;
                        }

                        @Override
                        public TaskId taskId() {
                            return new TaskId(0, 0);
                        }

                        @Override
                        public Serde<?> keySerde() {
                            return null;
                        }

                        @Override
                        public Serde<?> valueSerde() {
                            return null;
                        }

                        @Override
                        public File stateDir() {
                            return null;
                        }

                        @Override
                        public StreamsMetrics metrics() {
                            return null;
                        }

                        @Override
                        public void register(StateStore store, StateRestoreCallback stateRestoreCallback) {
                        }

                        @Override
                        public StateStore getStateStore(String name) {
                            return null;
                        }

                        @Override
                        public Cancellable schedule(long intervalMs, PunctuationType type, Punctuator callback) {
                            return null;
                        }

                        @Override
                        public <K, V> void forward(K key, V value) {
                        }

                        @Override
                        public <K, V> void forward(K key, V value, To to) {
                        }

                        @Override
                        public <K, V> void forward(K key, V value, int childIndex) {
                        }

                        @Override
                        public <K, V> void forward(K key, V value, String childName) {
                        }

                        @Override
                        public void commit() {
                        }

                        @Override
                        public String topic() {
                            return TEST_TOPIC;
                        }

                        @Override
                        public int partition() {
                            return 0;
                        }

                        @Override
                        public long offset() {
                            return 0;
                        }

                        @Override
                        public Headers headers() {
                            return headers;
                        }

                        @Override
                        public long timestamp() {
                            return 0;
                        }

                        @Override
                        public Map<String, Object> appConfigs() {
                            return null;
                        }

                        @Override
                        public Map<String, Object> appConfigsWithPrefix(String prefix) {
                            return null;
                        }
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
            kafkaStreamsTracing.processorSupplier(
                    "processor-1",
                    new AbstractProcessor<String, String>() {
                        @Override
                        public void process(String key, String value) {
                            context().forward(key, value);
                        }
                    });

    TransformerSupplier<String, String, String> fakeTransformerSupplier =
            kafkaStreamsTracing.transformerSupplier(
                    "transformer-1",
                    new Transformer<String, String, String>() {
                        ProcessorContext context;
                        @Override
                        public void init(ProcessorContext context) {
                            this.context = context;
                        }

                        @Override
                        public String transform(String key, String value) {
                            return "transformed";
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
