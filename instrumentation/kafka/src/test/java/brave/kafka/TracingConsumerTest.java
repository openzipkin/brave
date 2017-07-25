package brave.kafka;

import brave.Tracing;
import brave.sampler.Sampler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import zipkin.Span;
import zipkin.internal.Util;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class TracingConsumerTest {

    private static final String TEST_TOPIC = "myTopic";
    private static final String TEST_KEY = "foo";
    private static final String TEST_VALUE = "bar";

    private Tracing tracing;

    private ConcurrentLinkedDeque<Span> spans = new ConcurrentLinkedDeque<>();

    @Before
    public void init() throws IOException {
        tracing = Tracing.newBuilder()
                .reporter(spans::add)
                .sampler(Sampler.NEVER_SAMPLE)
                .build();
    }

    @Test
    public void should_call_wrapped_poll() {
        MockConsumer<String, String> consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        TopicPartition topicPartition = new TopicPartition(TEST_TOPIC, 0);

        Map<TopicPartition, Long> offsets = new HashMap<>();
        offsets.put(topicPartition, 0L);

        consumer.updateBeginningOffsets(offsets);
        consumer.assign(Collections.singleton(topicPartition));

        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>(TEST_TOPIC, 0, 0, TEST_KEY, TEST_VALUE);
        consumer.addRecord(consumerRecord);

        TracingConsumer<String, String> tracingConsumer = new TracingConsumer<>(tracing, consumer);
        tracingConsumer.poll(10);

        // offset changed
        assertThat(consumer.position(topicPartition)).isEqualTo(1L);
    }
}
