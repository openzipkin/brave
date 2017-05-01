package com.github.kristofa.brave.slf4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import com.github.kristofa.brave.SpanCollectorMetricsHandler;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Span;

public class AsyncSlf4jLoggingSpanCollectorTest {

    private static final String KEY1 = "key1";
    private static final String VALUE1 = "value1";
    private static final String KEY2 = "key1";
    private static final String VALUE2 = "value1";
    private static final int QUEUE_CAPACITY = AsyncSlf4jLoggingSpanCollector.DEFAULT_QUEUE_CAPACITY;

    private Logger mockLogger;
    private AsyncSlf4jLoggingSpanCollector spanCollector;
    private TestMetricsHander metrics;

    @Before
    public void setup() {
        mockLogger = mock(Logger.class);
        when(mockLogger.isInfoEnabled()).thenReturn(true);

        metrics = new TestMetricsHander();
        spanCollector = new AsyncSlf4jLoggingSpanCollector(
                mockLogger, metrics, 0 /* explicitly control flush */);
    }

    @Test
    public void testCollect() {
        final Span mockSpan = mock(Span.class);
        spanCollector.collect(mockSpan);

        verifyZeroInteractions(mockLogger);
        spanCollector.flush(); // manually flush the spans

        verify(mockLogger).isInfoEnabled();
        verify(mockLogger).info(mockSpan.toString());
        verifyNoMoreInteractions(mockLogger, mockSpan);
    }

    @Test
    public void testCollectMany() {
        List<Span> mockSpans = new ArrayList<>(QUEUE_CAPACITY);
        for (int i = 0; i < QUEUE_CAPACITY + 1; i++) {
            Span mockSpan = span(i, "test");
            spanCollector.collect(mockSpan);
            mockSpans.add(mockSpan);
        }

        verifyZeroInteractions(mockLogger);
        spanCollector.flush(); // manually flush the spans

        verify(mockLogger, times(QUEUE_CAPACITY)).isInfoEnabled();
        verify(mockLogger, times(QUEUE_CAPACITY)).info(anyString());

        assertThat(metrics.acceptedSpans.get()).isEqualTo(QUEUE_CAPACITY + 1);
        assertThat(metrics.droppedSpans.get()).isEqualTo(1);

        verifyNoMoreInteractions(mockLogger);
        verifyNoMoreInteractions(mockSpans.toArray());
    }

    @Test
    public void testCollectAfterAddingTwoDefaultAnnotations() {

        spanCollector.addDefaultAnnotation(KEY1, VALUE1);
        spanCollector.addDefaultAnnotation(KEY2, VALUE2);

        final Span mockSpan = span(1L, "test");
        spanCollector.collect(mockSpan);

        verifyZeroInteractions(mockLogger);
        spanCollector.flush(); // manually flush the spans

        // Create expected annotations.
        final BinaryAnnotation expectedBinaryAnnotation = BinaryAnnotation.create(KEY1, VALUE1, null);
        final BinaryAnnotation expectedBinaryAnnotation2 = BinaryAnnotation.create(KEY2, VALUE2, null);

        verify(mockSpan).addToBinary_annotations(expectedBinaryAnnotation);
        verify(mockSpan).addToBinary_annotations(expectedBinaryAnnotation2);
        verify(mockLogger).isInfoEnabled();
        verify(mockLogger).info(anyString());

        verifyNoMoreInteractions(mockLogger, mockSpan);
    }

    private Span span(long id, String name) {
        final Span mockSpan = mock(Span.class);
        when(mockSpan.getId()).thenReturn(id);
        when(mockSpan.getName()).thenReturn(name);
        return mockSpan;
    }

    private static class TestMetricsHander implements SpanCollectorMetricsHandler {

        final AtomicInteger acceptedSpans = new AtomicInteger();
        final AtomicInteger droppedSpans = new AtomicInteger();

        @Override
        public void incrementAcceptedSpans(int quantity) {
            acceptedSpans.addAndGet(quantity);
        }

        @Override
        public void incrementDroppedSpans(int quantity) {
            droppedSpans.addAndGet(quantity);
        }
    }
}
