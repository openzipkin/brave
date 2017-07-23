package brave.kafka;

import brave.Tracing;
import brave.context.log4j2.ThreadContextCurrentTraceContext;
import brave.internal.StrictCurrentTraceContext;
import brave.sampler.Sampler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.junit.Before;
import org.junit.Test;
import zipkin.Span;
import zipkin.internal.Util;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.assertj.core.api.Assertions.assertThat;

public class TracingConsumerTest {

    private static final String TEST_TOPIC = "myTopic";
    private static final String TEST_KEY = "foo";
    private static final String TEST_VALUE = "bar";

    private ConcurrentLinkedDeque<Span> spans = new ConcurrentLinkedDeque<>();
    private Tracing tracing;

    @Before
    public void init() throws IOException {
        spans.clear();
        tracing = tracingBuilder(Sampler.ALWAYS_SAMPLE).build();
    }

    @Test
    public void closeAsyncSpanFromHeaders() {

        final String traceId = "463ac35c9f6413ad";
        final String parentId = "463ac35c9f6413ab";
        final String spanId = "48485a3953bb6124";
        final String sampled = "1";


        MockConsumer<String, String> consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        TopicPartition topicPartition = new TopicPartition(TEST_TOPIC, 0);

        Map<TopicPartition, Long> offsets = new HashMap<>();
        offsets.put(topicPartition, 0L);

        consumer.updateBeginningOffsets(offsets);
        consumer.assign(Collections.singleton(topicPartition));

        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>(TEST_TOPIC, 0, 1, TEST_KEY, TEST_VALUE);
        consumerRecord.headers()
                .add("X-B3-TraceId", traceId.getBytes(Util.UTF_8))
                .add("X-B3-ParentSpanId", parentId.getBytes(Util.UTF_8))
                .add("X-B3-SpanId", spanId.getBytes(Util.UTF_8))
                .add("X-B3-Sampled", sampled.getBytes(Util.UTF_8));
        consumer.addRecord(consumerRecord);

        TracingConsumer<String, String> tracingConsumer = new TracingConsumer<>(tracing, consumer);
        tracingConsumer.poll(10);

        // Span ID should be the same as we are closing the producer span.
        assertThat(spans)
                .extracting(s -> Long.toHexString(s.id))
                .containsExactly(spanId);
    }

    private Tracing.Builder tracingBuilder(Sampler sampler) {
        return Tracing.newBuilder()
                .reporter(spans::add)
                .currentTraceContext( // connect to log4
                        ThreadContextCurrentTraceContext.create(new StrictCurrentTraceContext()))
                .sampler(sampler);
    }
}
