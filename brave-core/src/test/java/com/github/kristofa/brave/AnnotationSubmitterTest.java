package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import zipkin.Constants;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(PowerMockRunner.class)
@PrepareForTest(AnnotationSubmitter.DefaultClock.class)
public class AnnotationSubmitterTest {

    private final static long START_TIME_MICROSECONDS = System.currentTimeMillis() * 1000;

    private final static String ANNOTATION_NAME = "AnnotationName";
    private static final String KEY = "key";
    private static final String STRING_VALUE = "stringValue";
    private static final int INT_VALUE = 23;

    private Endpoint endpoint =
        Endpoint.builder().serviceName("foobar").ipv4(127 << 24 | 1).port(9999).build();
    private Span span = Brave.toSpan(SpanId.builder().spanId(1).build());
    private AnnotationSubmitter annotationSubmitter;
    private List<zipkin.Span> spans = new ArrayList<>();

    @Before
    public void setup() {
        PowerMockito.mockStatic(System.class);
        PowerMockito.when(System.currentTimeMillis()).thenReturn(START_TIME_MICROSECONDS / 1000);
        PowerMockito.when(System.nanoTime()).thenReturn(0L);
        annotationSubmitter = newAnnotationSubmitter();
    }

    @Test
    public void testSubmitAnnotationSpanEndpointString() {
        PowerMockito.when(System.nanoTime()).thenReturn(1000L);

        annotationSubmitter.submitAnnotation(ANNOTATION_NAME);

        assertThat(span.getAnnotations()).containsExactly(
            Annotation.create(
                START_TIME_MICROSECONDS + 1,
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
        Recorder.Default recorder = (Recorder.Default) annotationSubmitter.recorder();
        assertThat(recorder.clock().currentTimeMicroseconds())
            .isEqualTo(START_TIME_MICROSECONDS);
    }

    @Test
    public void testCurrentTimeMicroSeconds_fromRelativeNanoTick() {
        AnnotationSubmitter.Clock clock = new AnnotationSubmitter.DefaultClock();
        PowerMockito.when(System.nanoTime()).thenReturn(1000L);

        assertThat(clock.currentTimeMicroseconds())
            .isEqualTo(START_TIME_MICROSECONDS + 1L);
    }

    @Test
    public void doesntSetDurationWhenTimestampUnset() {
        annotationSubmitter.submitEndAnnotation(Constants.SERVER_SEND);
        assertThat(spans).allSatisfy(
            span -> {
                assertThat(span.timestamp).isNull();
                assertThat(span.duration).isNull();
            }
        );
    }

    @Test
    public void setsDurationWhenTimestampPresentButStartTickAbsent() {
        span.setTimestamp(START_TIME_MICROSECONDS - 1);

        annotationSubmitter.submitStartAnnotation(Constants.SERVER_RECV);
        annotationSubmitter.submitEndAnnotation(Constants.SERVER_SEND);
        assertThat(spans).extracting(s -> s.duration)
            .containsExactly(1L);
    }

    @Test
    public void durationRoundedUpToOneMicro() {
        span.setTimestamp(START_TIME_MICROSECONDS);

        PowerMockito.when(System.nanoTime()).thenReturn(787L);

        annotationSubmitter.submitStartAnnotation(Constants.SERVER_RECV);
        annotationSubmitter.submitEndAnnotation(Constants.SERVER_SEND);
        assertThat(spans).extracting(s -> s.duration)
            .containsExactly(1L);
    }

    AnnotationSubmitter newAnnotationSubmitter() {
        CurrentSpan currentSpan = new CurrentSpan() {
            @Override Span get() {
                return span;
            }
        };
        AnnotationSubmitter.DefaultClock clock = new AnnotationSubmitter.DefaultClock();
        Recorder recorder = new AutoValue_Recorder_Default(endpoint, clock, spans::add);
        return new AnnotationSubmitter(){
            @Override CurrentSpan currentSpan() {
                return currentSpan;
            }

            @Override Recorder recorder() {
                return recorder;
            }
        };
    }
}
