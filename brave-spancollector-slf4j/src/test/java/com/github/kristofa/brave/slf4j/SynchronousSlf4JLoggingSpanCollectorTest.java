package com.github.kristofa.brave.slf4j;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Span;

public class SynchronousSlf4JLoggingSpanCollectorTest {

    private static final String KEY1 = "key1";
    private static final String VALUE1 = "value1";
    private static final String KEY2 = "key1";
    private static final String VALUE2 = "value1";

    private Logger mockLogger;
    private SynchronousSlf4jLoggingSpanCollector spanCollector;

    @Before
    public void setup() {
        mockLogger = mock(Logger.class);
        when(mockLogger.isInfoEnabled()).thenReturn(true);
        spanCollector = new SynchronousSlf4jLoggingSpanCollector(mockLogger);
    }

    @Test
    public void testCollect() {
        final Span mockSpan = mock(Span.class);
        spanCollector.collect(mockSpan);

        verify(mockLogger).isInfoEnabled();
        verify(mockLogger).info(mockSpan.toString());
        verifyNoMoreInteractions(mockLogger, mockSpan);
    }

    @Test
    public void testCollectMany() {
        final int expectedSpansLogged = 1000;
        List<Span> mockSpans = new ArrayList<>(expectedSpansLogged);
        for (int i = 0; i < expectedSpansLogged; i++) {
            Span mockSpan = span(i, "test");
            spanCollector.collect(mockSpan);
            mockSpans.add(mockSpan);
        }

        verify(mockLogger, times(expectedSpansLogged)).isInfoEnabled();
        verify(mockLogger, times(expectedSpansLogged)).info(anyString());

        verifyNoMoreInteractions(mockLogger);
        verifyNoMoreInteractions(mockSpans.toArray());
    }

    @Test
    public void testCollectAfterAddingTwoDefaultAnnotations() {

        spanCollector.addDefaultAnnotation(KEY1, VALUE1);
        spanCollector.addDefaultAnnotation(KEY2, VALUE2);

        verifyZeroInteractions(mockLogger);

        final Span mockSpan = span(1L, "test");
        spanCollector.collect(mockSpan);

        // Create expected annotations.
        final BinaryAnnotation expectedBinaryAnnotation = BinaryAnnotation.create(KEY1, VALUE1, null);
        final BinaryAnnotation expectedBinaryAnnotation2 = BinaryAnnotation.create(KEY2, VALUE2, null);

        verify(mockSpan).addToBinary_annotations(expectedBinaryAnnotation);
        verify(mockSpan).addToBinary_annotations(expectedBinaryAnnotation2);
        verify(mockLogger).isInfoEnabled();
        verify(mockLogger).info(mockSpan.toString());

        verifyNoMoreInteractions(mockLogger, mockSpan);
    }

    private Span span(long id, String name) {
        final Span mockSpan = mock(Span.class);
        when(mockSpan.getId()).thenReturn(id);
        when(mockSpan.getName()).thenReturn(name);
        return mockSpan;
    }

}
