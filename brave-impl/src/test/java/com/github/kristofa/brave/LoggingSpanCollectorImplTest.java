package com.github.kristofa.brave;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.nio.ByteBuffer;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.slf4j.Logger;

import com.twitter.zipkin.gen.AnnotationType;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Span;

public class LoggingSpanCollectorImplTest {

    private static final String KEY1 = "key1";
    private static final String VALUE1 = "value1";
    private static final String KEY2 = "key1";
    private static final String VALUE2 = "value1";
    private LoggingSpanCollectorImpl spanCollector;
    private Logger mockLogger;

    @Before
    public void setup() {

        mockLogger = mock(Logger.class);

        spanCollector = new LoggingSpanCollectorImpl() {

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
        verify(mockLogger).info(mockSpan.toString());
        verifyNoMoreInteractions(mockLogger, mockSpan);
    }

    @Test
    public void testCollectAfterAddingDefaultAnnotations() {

        spanCollector.addDefaultAnnotation(KEY1, VALUE1);

        final Span mockSpan = mock(Span.class);
        spanCollector.collect(mockSpan);

        // Create expected annotation.
        final BinaryAnnotation expectedBinaryAnnoration = create(KEY1, VALUE1);

        final InOrder inOrder = inOrder(mockSpan, mockLogger);

        inOrder.verify(mockSpan).addToBinary_annotations(expectedBinaryAnnoration);
        inOrder.verify(mockLogger).info(mockSpan.toString());

        verifyNoMoreInteractions(mockLogger, mockSpan);
    }

    @Test
    public void testCollectAfterAddingTwoDefaultAnnotations() {

        spanCollector.addDefaultAnnotation(KEY1, VALUE1);
        spanCollector.addDefaultAnnotation(KEY2, VALUE2);

        final Span mockSpan = mock(Span.class);
        spanCollector.collect(mockSpan);

        // Create expected annotations.
        final BinaryAnnotation expectedBinaryAnnoration = create(KEY1, VALUE1);
        final BinaryAnnotation expectedBinaryAnnoration2 = create(KEY2, VALUE2);

        verify(mockSpan).addToBinary_annotations(expectedBinaryAnnoration);
        verify(mockSpan).addToBinary_annotations(expectedBinaryAnnoration2);
        verify(mockLogger).info(mockSpan.toString());

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
    public void testClose() {
        spanCollector.close();
        verifyNoMoreInteractions(mockLogger);
    }

    @Test
    public void testGetLogger() {
        final LoggingSpanCollectorImpl loggingSpanCollectorImpl = new LoggingSpanCollectorImpl();
        assertNotNull(loggingSpanCollectorImpl.getLogger());

    }

    private BinaryAnnotation create(final String key, final String value) {
        // Create expected annotation.
        final ByteBuffer bb = ByteBuffer.wrap(value.getBytes());
        final BinaryAnnotation expectedBinaryAnnoration = new BinaryAnnotation();
        expectedBinaryAnnoration.setKey(key);
        expectedBinaryAnnoration.setValue(bb);
        expectedBinaryAnnoration.setAnnotation_type(AnnotationType.STRING);
        return expectedBinaryAnnoration;
    }

}
