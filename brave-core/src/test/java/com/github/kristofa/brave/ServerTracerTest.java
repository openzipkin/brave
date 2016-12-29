package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import zipkin.Constants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
// Added to declutter console: tells power mock not to mess with implicit classes we aren't testing
@PowerMockIgnore({"org.apache.logging.*", "javax.script.*"})
@PrepareForTest(AnnotationSubmitter.DefaultClock.class)
public class ServerTracerTest {

    private static final long START_TIME_MICROSECONDS = System.currentTimeMillis() * 1000;
    private static final long TRACE_ID = 1;
    private static final SpanId CONTEXT = SpanId.builder().traceId(TRACE_ID).spanId(2).parentId(3L).build();
    private static final String SPAN_NAME = "span name";

    private ServerTracer serverTracer;
    private SpanCollector mockSpanCollector;
    private Endpoint endpoint = Endpoint.create("service", 0);
    private Sampler mockSampler;
    private Span span = Brave.newSpan(CONTEXT);

    @Before
    public void setup() {
        mockSpanCollector = mock(SpanCollector.class);
        mockSampler = mock(Sampler.class);

        PowerMockito.mockStatic(System.class);
        PowerMockito.when(System.currentTimeMillis()).thenReturn(START_TIME_MICROSECONDS / 1000);
        PowerMockito.when(System.nanoTime()).thenReturn(0L);

        serverTracer = braveBuilder().build().serverTracer();
    }

    @Test
    public void testClearCurrentSpan() {
        serverTracer.clearCurrentSpan();
        assertThat(serverTracer.currentSpan().get()).isNull();
    }

    @Test
    public void testSetStateCurrentTrace() {
        serverTracer.setStateCurrentTrace(CONTEXT, SPAN_NAME);

        assertThat(serverTracer.currentSpan().getCurrentServerSpan())
            .isEqualTo(ServerSpan.create(CONTEXT, SPAN_NAME));
    }

    @Test
    public void testSetStateNoTracing() {
        serverTracer.setStateNoTracing();

        assertThat(serverTracer.currentSpan().getCurrentServerSpan().getSample())
            .isFalse();
    }

    @Test
    public void testSetStateUnknownSamplerTrue() {
        when(mockSampler.isSampled(anyLong())).thenReturn(true);

        serverTracer.setStateUnknown(SPAN_NAME);

        assertThat(serverTracer.currentSpan().getCurrentServerSpan().getSample())
            .isTrue();
    }

    @Test
    public void testSetStateUnknownSamplerTrue_128Bit() {
        serverTracer = braveBuilder().traceId128Bit(true).build().serverTracer();

        when(mockSampler.isSampled(anyLong())).thenReturn(true);

        serverTracer.setStateUnknown(SPAN_NAME);

        assertThat(serverTracer.currentSpan().getCurrentServerSpan().getSample())
            .isTrue();
    }

    @Test
    public void testSetStateUnknownSamplerFalse() {
        when(mockSampler.isSampled(anyLong())).thenReturn(false);

        serverTracer.setStateUnknown(SPAN_NAME);

        assertThat(serverTracer.currentSpan().getCurrentServerSpan())
            .isEqualTo(ServerSpan.NOT_SAMPLED);
    }

    @Test
    public void testSetServerReceivedNoServerSpan() {
        serverTracer.currentSpan().setCurrentSpan(null);

        serverTracer.setServerReceived();

        assertThat(serverTracer.currentSpan().getCurrentServerSpan())
            .isEqualTo(ServerSpan.EMPTY);
    }

    @Test
    public void testSetServerReceived() {
        ServerSpan serverSpan = new AutoValue_ServerSpan(CONTEXT, span, true);
        serverTracer.currentSpan().setCurrentSpan(serverSpan);

        serverTracer.setServerReceived();

        Annotation expectedAnnotation = Annotation.create(
            START_TIME_MICROSECONDS,
            Constants.SERVER_RECV,
            endpoint
        );

        assertEquals(START_TIME_MICROSECONDS, span.getTimestamp().longValue());
        assertEquals(expectedAnnotation, span.getAnnotations().get(0));
    }

    @Test
    public void testSetServerReceivedSentClientAddress() {
        ServerSpan serverSpan = new AutoValue_ServerSpan(CONTEXT, span, true);
        serverTracer.currentSpan().setCurrentSpan(serverSpan);

        serverTracer.setServerReceived(Endpoint.builder()
            .ipv4(1 << 24 | 2 << 16 | 3 << 8 | 4)
            .port(9999)
            .serviceName("foobar").build());

        Annotation expectedAnnotation = Annotation.create(
            START_TIME_MICROSECONDS,
            Constants.SERVER_RECV,
            endpoint
        );

        assertEquals(START_TIME_MICROSECONDS, span.getTimestamp().longValue());
        assertEquals(expectedAnnotation, span.getAnnotations().get(0));

        BinaryAnnotation serverAddress = BinaryAnnotation.address(
            Constants.CLIENT_ADDR,
            Endpoint.builder().serviceName("foobar").ipv4(1 << 24 | 2 << 16 | 3 << 8 | 4).port(9999).build()
        );
        assertEquals(serverAddress, span.getBinary_annotations().get(0));
    }

    @Test
    public void testSetServerReceivedSentClientAddress_noServiceName() {
        ServerSpan serverSpan = new AutoValue_ServerSpan(CONTEXT, span, true);
        serverTracer.currentSpan().setCurrentSpan(serverSpan);

        serverTracer.setServerReceived(1 << 24 | 2 << 16 | 3 << 8 | 4, 9999, null);

        assertEquals("unknown", span.getBinary_annotations().get(0).host.service_name);
    }

    @Test
    public void testSetServerSendShouldNoServerSpan() {
        serverTracer.currentSpan().setCurrentSpan(null);

        serverTracer.setServerSend();

        verifyNoMoreInteractions(mockSpanCollector);
    }

    @Test
    public void testSetServerSend() {
        span.setTimestamp(100L);
        ServerSpan serverSpan = new AutoValue_ServerSpan(CONTEXT, span, true);
        serverTracer.currentSpan().setCurrentSpan(serverSpan);

        serverTracer.setServerSend();

        Annotation expectedAnnotation = Annotation.create(
            START_TIME_MICROSECONDS,
            Constants.SERVER_SEND,
            endpoint
        );

        verify(mockSpanCollector).collect(span);

        assertThat(serverTracer.currentSpan().getCurrentServerSpan())
            .isEqualTo(ServerSpan.EMPTY);

        assertEquals(START_TIME_MICROSECONDS - span.getTimestamp().longValue(), span.getDuration().longValue());
        assertEquals(expectedAnnotation, span.getAnnotations().get(0));
    }

    @Test
    public void setServerSend_skipsDurationWhenNoTimestamp() {
        // duration unset due to client-originated trace
        ServerSpan serverSpan = new AutoValue_ServerSpan(CONTEXT, span, true);
        serverTracer.currentSpan().setCurrentSpan(serverSpan);

        serverTracer.setServerSend();

        verify(mockSpanCollector).collect(span);
        verifyNoMoreInteractions(mockSpanCollector);

        assertThat(span.getDuration()).isNull();
    }

    @Test
    public void setServerSend_usesPreciseDuration() {
        span.setTimestamp(START_TIME_MICROSECONDS);
        ServerSpan serverSpan = new AutoValue_ServerSpan(CONTEXT, span, true);
        serverTracer.currentSpan().setCurrentSpan(serverSpan);

        PowerMockito.when(System.nanoTime()).thenReturn(500000L);

        serverTracer.setServerSend();

        verify(mockSpanCollector).collect(span);
        verifyNoMoreInteractions(mockSpanCollector);

        assertEquals(500L, span.getDuration().longValue());
    }

    /** Duration of less than one microsecond is confusing to plot and could coerce to null. */
    @Test
    public void setServerSend_lessThanMicrosRoundUp() {
        span.setTimestamp(START_TIME_MICROSECONDS);
        ServerSpan serverSpan = new AutoValue_ServerSpan(CONTEXT, span, true);
        serverTracer.currentSpan().setCurrentSpan(serverSpan);

        PowerMockito.when(System.nanoTime()).thenReturn(50L);

        serverTracer.setServerSend();

        verify(mockSpanCollector).collect(span);
        verifyNoMoreInteractions(mockSpanCollector);

        assertEquals(1L, span.getDuration().longValue());
    }

    Brave.Builder braveBuilder() {
        return new Brave.Builder(endpoint)
            .spanCollector(mockSpanCollector)
            .traceSampler(mockSampler);
    }
}
