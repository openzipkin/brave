package brave.kafka;


import brave.Tracing;
import brave.context.log4j2.ThreadContextCurrentTraceContext;
import brave.internal.StrictCurrentTraceContext;
import brave.sampler.Sampler;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.Before;
import org.junit.Test;
import zipkin.Span;
import zipkin.internal.Util;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

import static brave.kafka.TracingProducer.KAFKA_KEY_TAG;
import static brave.kafka.TracingProducer.KAFKA_TOPIC_TAG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class TracingProducerTest {

    private static final String TEST_TOPIC = "myTopic";
    private static final String TEST_KEY = "foo";
    private static final String TEST_VALUE = "bar";

    private ConcurrentLinkedDeque<Span> spans = new ConcurrentLinkedDeque<>();
    private Tracing tracing;

    @Before
    public void setup() throws Exception {
        spans.clear();
        tracing = tracingBuilder(Sampler.ALWAYS_SAMPLE).build();
    }

    @Test
    public void producerAddsB3Headers() {
        MockProducer<String, String> mockProducer = new MockProducer<>();
        TracingProducer<String, String> tracingProducer = new TracingProducer<>(tracing, mockProducer);
        tracingProducer.send(new ProducerRecord<>(TEST_TOPIC, TEST_KEY, TEST_VALUE));

        List<String> expectedHeaders = Arrays.asList(
                "X-B3-TraceId", "X-B3-SpanId", "X-B3-Sampled");

        List<String> headerKeys = Arrays.stream(mockProducer.history().get(0).headers().toArray())
                .map(Header::key)
                .collect(Collectors.toList());


        assertNotNull("Span was not created", spans.getLast());
        assertTrue("Missing expected headers", headerKeys.containsAll(expectedHeaders));

        assertThat(spans)
                .flatExtracting(span -> span.binaryAnnotations)
                .filteredOn(annotation -> annotation.key.equals(KAFKA_TOPIC_TAG) || annotation.key.equals(KAFKA_KEY_TAG))
                .extracting(key -> new String(key.value, Util.UTF_8))
                .containsExactlyInAnyOrder(TEST_TOPIC, TEST_KEY);

        assertThat(spans)
                .flatExtracting(s -> s.annotations)
                .extracting(a -> a.value)
                .containsExactly("cs");
    }

    private Tracing.Builder tracingBuilder(Sampler sampler) {
        return Tracing.newBuilder()
                .reporter(spans::add)
                .currentTraceContext( // connect to log4
                        ThreadContextCurrentTraceContext.create(new StrictCurrentTraceContext()))
                .sampler(sampler);
    }
}