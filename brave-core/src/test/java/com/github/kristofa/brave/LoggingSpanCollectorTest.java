package com.github.kristofa.brave;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Span;

public class LoggingSpanCollectorTest {

    private static final String KEY1 = "key1";
    private static final String VALUE1 = "value1";
    private static final String KEY2 = "key1";
    private static final String VALUE2 = "value1";
    private LoggingSpanCollector spanCollector;
    private Logger mockLogger;

    @Before
    public void setup() {

        mockLogger = mock(Logger.class);

        spanCollector = new LoggingSpanCollector() {

            @Override
            Logger getLogger() {
                return mockLogger;
            }
        };
    }

    @Test
    public void testCollect() {
        final Span mockSpan = mock(Span.class);
        spanCollector.collect(mockSpan);
        verify(mockLogger).isLoggable(Level.INFO);
        verifyNoMoreInteractions(mockLogger, mockSpan);
    }

    @Test
    public void testCollectAfterAddingDefaultAnnotations() {

        spanCollector.addDefaultAnnotation(KEY1, VALUE1);

        final Span mockSpan = mock(Span.class);
        spanCollector.collect(mockSpan);

        // Create expected annotation.
        final BinaryAnnotation expectedBinaryAnnotation = BinaryAnnotation.create(KEY1, VALUE1, null);

        final InOrder inOrder = inOrder(mockSpan, mockLogger);

        inOrder.verify(mockSpan).addToBinary_annotations(expectedBinaryAnnotation);
        inOrder.verify(mockLogger).isLoggable(Level.INFO);

        verifyNoMoreInteractions(mockLogger, mockSpan);
    }

    @Test
    public void testCollectAfterAddingTwoDefaultAnnotations() {

        spanCollector.addDefaultAnnotation(KEY1, VALUE1);
        spanCollector.addDefaultAnnotation(KEY2, VALUE2);

        final Span mockSpan = mock(Span.class);
        spanCollector.collect(mockSpan);

        // Create expected annotations.
        final BinaryAnnotation expectedBinaryAnnotation = BinaryAnnotation.create(KEY1, VALUE1, null);
        final BinaryAnnotation expectedBinaryAnnotation2 = BinaryAnnotation.create(KEY2, VALUE2, null);

        verify(mockSpan).addToBinary_annotations(expectedBinaryAnnotation);
        verify(mockSpan).addToBinary_annotations(expectedBinaryAnnotation2);
        verify(mockLogger).isLoggable(Level.INFO);

        verifyNoMoreInteractions(mockLogger, mockSpan);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddDefaultAnnotationEmptyKey() {
        spanCollector.addDefaultAnnotation("", VALUE1);
    }

    @Test(expected = NullPointerException.class)
    public void testAddDefaultAnnotationNullKey() {
        spanCollector.addDefaultAnnotation(null, VALUE1);
    }

    @Test(expected = NullPointerException.class)
    public void testAddDefaultAnnotationNullValue() {
        spanCollector.addDefaultAnnotation(KEY1, null);
    }

    @Test
    public void testGetLogger() {
        final LoggingSpanCollector loggingSpanCollector = new LoggingSpanCollector();
        assertNotNull(loggingSpanCollector.getLogger());

    }
}
