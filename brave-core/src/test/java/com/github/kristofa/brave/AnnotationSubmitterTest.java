package com.github.kristofa.brave;

import com.github.kristofa.brave.SpanAndEndpoint.StaticSpanAndEndpoint;
import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import java.io.UnsupportedEncodingException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(PowerMockRunner.class)
@PrepareForTest(AnnotationSubmitter.class)
public class AnnotationSubmitterTest {

    private final static long CURRENT_TIME_MICROSECONDS = System.currentTimeMillis() * 1000;

    private final static String ANNOTATION_NAME = "AnnotationName";
    private static final String KEY = "key";
    private static final String STRING_VALUE = "stringValue";
    private static final int INT_VALUE = 23;

    private Endpoint endpoint =
        Endpoint.builder().serviceName("foobar").ipv4(127 << 24 | 1).port(9999).build();
    private Span span = new Span().setName("foo");
    private AnnotationSubmitter annotationSubmitter =
        AnnotationSubmitter.create(StaticSpanAndEndpoint.create(span, endpoint));;

    @Before
    public void setup() {
        PowerMockito.mockStatic(System.class);
        PowerMockito.when(System.currentTimeMillis()).thenReturn(CURRENT_TIME_MICROSECONDS / 1000);
    }

    @Test
    public void testSubmitAnnotationSpanEndpointString() {
        annotationSubmitter.submitAnnotation(ANNOTATION_NAME);

        assertThat(span.getAnnotations()).containsExactly(
            Annotation.create(
                CURRENT_TIME_MICROSECONDS,
                ANNOTATION_NAME,
                endpoint
            )
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSubmitBinaryAnnotationStringValueEmptyKey() {
        annotationSubmitter.submitBinaryAnnotation(" ", STRING_VALUE);
    }

    @Test(expected = NullPointerException.class)
    public void testSubmitBinaryAnnotationStringValueNullValue() {
        annotationSubmitter.submitBinaryAnnotation(KEY, null);
    }

    @Test
    public void testSubmitBinaryAnnotationStringValue() throws UnsupportedEncodingException {
        annotationSubmitter.submitBinaryAnnotation(KEY, STRING_VALUE);

        assertThat(span.getBinary_annotations()).containsExactly(
            BinaryAnnotation.create(
                KEY,
                STRING_VALUE,
                endpoint
            )
        );
    }

    @Test
    public void testSubmitBinaryAnnotationIntValue() {
        annotationSubmitter.submitBinaryAnnotation(KEY, INT_VALUE);

        assertThat(span.getBinary_annotations()).containsExactly(
            BinaryAnnotation.create(
                KEY,
                String.valueOf(INT_VALUE),
                endpoint
            )
        );
    }

    @Test
    public void testCurrentTimeMicroSeconds_fromSystemCurrentMillis() {
        assertThat(annotationSubmitter.clock().currentTimeMicroseconds())
            .isEqualTo(CURRENT_TIME_MICROSECONDS);
    }

    @Test
    public void testCurrentTimeMicroSeconds_fromRelativeNanoTick() {
        PowerMockito.when(System.nanoTime()).thenReturn(1000L);

        assertThat(annotationSubmitter.currentTimeMicroseconds(CURRENT_TIME_MICROSECONDS, 0L))
            .isEqualTo(CURRENT_TIME_MICROSECONDS + 1L);
    }

    @Test
    public void annotationTimestampRelativeToStart() {
        PowerMockito.when(System.nanoTime()).thenReturn(1000L);

        span.setTimestamp(CURRENT_TIME_MICROSECONDS);
        span.startTick = 0L;

        annotationSubmitter.submitAnnotation(ANNOTATION_NAME);

        assertThat(span.getAnnotations()).containsExactly(
            Annotation.create(
                CURRENT_TIME_MICROSECONDS + 1,
                ANNOTATION_NAME,
                endpoint
            )
        );
    }

    @Test
    public void doesntSetDurationWhenTimestampUnset() {
        annotationSubmitter.submitAnnotation("sr");
        annotationSubmitter.submitEndAnnotation("ss", span -> {
            assertThat(span.timestamp).isNull();
            assertThat(span.duration).isNull();
        });
    }

    @Test
    public void setsDurationWhenTimestampPresentButStartTickAbsent() {
        span.setTimestamp(CURRENT_TIME_MICROSECONDS - 1);

        annotationSubmitter.submitAnnotation("sr");
        annotationSubmitter.submitEndAnnotation("ss", span ->
            assertThat(span.duration).isEqualTo(1));
    }

    @Test
    public void durationRoundedUpToOneMicro() {
        span.setTimestamp(CURRENT_TIME_MICROSECONDS);
        span.startTick = 0L;

        PowerMockito.when(System.nanoTime()).thenReturn(787L);

        annotationSubmitter.submitAnnotation("sr");
        annotationSubmitter.submitEndAnnotation("ss", span ->
            assertThat(span.duration).isEqualTo(1L));
    }
}
