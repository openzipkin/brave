package com.github.kristofa.brave.kafka;

import com.github.charithe.kafka.KafkaJunitRule;
import com.github.kristofa.brave.SpanCollectorMetricsHandler;
import com.twitter.zipkin.gen.Span;
import kafka.serializer.DefaultDecoder;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.junit.Rule;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.assertEquals;

public class ITKafkaSpanCollector {

    private final EventsHandler metricsHandler = new EventsHandler();

    private static class EventsHandler implements SpanCollectorMetricsHandler {

        public int acceptedSpans = 0;
        public int droppedSpans = 0;

        @Override
        public synchronized void incrementAcceptedSpans(int quantity) {
            acceptedSpans += quantity;
        }

        @Override
        public synchronized void incrementDroppedSpans(int quantity) {
            droppedSpans += quantity;
        }
    }


    @Rule
    public KafkaJunitRule kafkaRule = new KafkaJunitRule();

    @Test
    public void submitSingleSpan() throws TException, TimeoutException {

        KafkaSpanCollector kafkaCollector = new KafkaSpanCollector("localhost:"+kafkaRule.kafkaBrokerPort(), metricsHandler);
        Span span = span(1l, "test_kafka_span");
        kafkaCollector.collect(span);
        kafkaCollector.close();

        List<Span> spans = getCollectedSpans(kafkaRule.readMessages("zipkin", 1, new DefaultDecoder(kafkaRule.consumerConfig().props())));
        assertEquals(1, spans.size());
        assertEquals(span, spans.get(0));
        assertEquals(1, metricsHandler.acceptedSpans);
        assertEquals(0, metricsHandler.droppedSpans);

    }

    @Test
    public void submitMultipleSpansInParallel() throws InterruptedException, ExecutionException, TimeoutException, TException {
        KafkaSpanCollector kafkaCollector = new KafkaSpanCollector("localhost:"+kafkaRule.kafkaBrokerPort(), metricsHandler);
        Callable<Void> spanProducer1 = new Callable<Void>() {

            @Override
            public Void call() throws Exception {
                for(int i=1; i<=200; i++)
                {
                    kafkaCollector.collect(span(i, "producer1_" + i));
                }
                return null;
            }
        };

        Callable<Void> spanProducer2 = new Callable<Void>() {

            @Override
            public Void call() throws Exception {
                for(int i=1; i<=200; i++)
                {
                    kafkaCollector.collect(span(i, "producer2_"+i));
                }
                return null;
            }
        };

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        Future<Void> future1 = executorService.submit(spanProducer1);
        Future<Void> future2 = executorService.submit(spanProducer2);

        future1.get(2000, TimeUnit.MILLISECONDS);
        future2.get(2000, TimeUnit.MILLISECONDS);

        List<Span> spans = getCollectedSpans(kafkaRule.readMessages("zipkin", 400, new DefaultDecoder(kafkaRule.consumerConfig().props())));
        assertEquals(400, spans.size());
        assertEquals(400, metricsHandler.acceptedSpans);
        assertEquals(0, metricsHandler.droppedSpans);
        kafkaCollector.close();
    }

    private List<Span> getCollectedSpans(List<byte[]> rawSpans) throws TException {

        TDeserializer deserializer = new TDeserializer(new TBinaryProtocol.Factory());
        List<Span> spans = new ArrayList<>();

        for (byte[] rawSpan : rawSpans) {
            Span span = new Span();
            deserializer.deserialize(span, rawSpan);
            spans.add(span);
        }
        return spans;
    }

    private Span span(long traceId, String spanName) {
        final Span span = new Span();
        span.setId(traceId);
        span.setTrace_id(traceId);
        span.setName(spanName);
        return span;
    }
}
