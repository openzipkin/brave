package com.github.kristofa.brave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Random;

import com.github.kristofa.brave.example.TestServerClientAndLocalSpanStateCompilation;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.AnnotationType;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.zipkinCoreConstants;

@RunWith(PowerMockRunner.class)
@PrepareForTest(AnnotationSubmitter.class)
public class ClientTracerTest {

    private final static long CURRENT_TIME_MICROSECONDS = System.currentTimeMillis() * 1000;
    private final static String REQUEST_NAME = "requestname";
    private static final long PARENT_SPAN_ID = 103;
    private static final long PARENT_TRACE_ID = 105;
    private static final long TRACE_ID = 32534;

    private ServerClientAndLocalSpanState state = new TestServerClientAndLocalSpanStateCompilation();
    private Random mockRandom;
    private SpanCollector mockCollector;
    private ClientTracer clientTracer;
    private Span mockSpan;
    private Sampler mockSampler;

    @Before
    public void setup() {
        mockSampler = mock(Sampler.class);
        mockRandom = mock(Random.class);
        mockCollector = mock(SpanCollector.class);
        mockSpan = mock(Span.class);

        PowerMockito.mockStatic(System.class);
        PowerMockito.when(System.currentTimeMillis()).thenReturn(CURRENT_TIME_MICROSECONDS / 1000);
        clientTracer = ClientTracer.builder()
            .state(state)
            .randomGenerator(mockRandom)
            .spanCollector(mockCollector)
            .traceSampler(mockSampler)
            .build();
    }

    @Test
    public void testSetClientSentNoClientSpan() {
        state.setCurrentClientSpan(null);
        clientTracer.setClientSent();
        verifyNoMoreInteractions(mockCollector, mockSampler);
    }

    @Test
    public void testSetClientSent() {
        Span clientSent = new Span();
        state.setCurrentClientSpan(clientSent);
        clientTracer.setClientSent();

        final Annotation expectedAnnotation = new Annotation();
        expectedAnnotation.setHost(state.getClientEndpoint());
        expectedAnnotation.setValue(zipkinCoreConstants.CLIENT_SEND);
        expectedAnnotation.setTimestamp(CURRENT_TIME_MICROSECONDS);
        verifyNoMoreInteractions(mockCollector, mockSampler);

        assertEquals(CURRENT_TIME_MICROSECONDS, clientSent.timestamp);
        assertEquals(expectedAnnotation, clientSent.annotations.get(0));
    }

    @Test
    public void testSetClientSentServerAddress() {
        Span clientSent = new Span();
        state.setCurrentClientSpan(clientSent);

        clientTracer.setClientSent(1 << 24 | 2 << 16 | 3 << 8 | 4, 9999, "foobar");

        final Annotation expectedAnnotation = new Annotation();
        expectedAnnotation.setHost(state.getClientEndpoint());
        expectedAnnotation.setValue(zipkinCoreConstants.CLIENT_SEND);
        expectedAnnotation.setTimestamp(CURRENT_TIME_MICROSECONDS);
        verifyNoMoreInteractions(mockCollector, mockSampler);

        assertEquals(CURRENT_TIME_MICROSECONDS, clientSent.timestamp);
        assertEquals(expectedAnnotation, clientSent.annotations.get(0));

        BinaryAnnotation serverAddress = new BinaryAnnotation()
            .setKey(zipkinCoreConstants.SERVER_ADDR)
            .setValue(new byte[]{1})
            .setAnnotation_type(AnnotationType.BOOL)
            .setHost(new Endpoint()
                .setService_name("foobar")
                .setIpv4(1 << 24 | 2 << 16 | 3 << 8 | 4)
                .setPort((short) 9999));
        assertEquals(serverAddress, clientSent.binary_annotations.get(0));
    }

    @Test
    public void testSetClientSentServerAddress_noServiceName() {
        Span clientSent = new Span();
        state.setCurrentClientSpan(clientSent);

        clientTracer.setClientSent(1 << 24 | 2 << 16 | 3 << 8 | 4, 9999, null);

        assertEquals("unknown", clientSent.binary_annotations.get(0).getHost().getService_name());
    }

    @Test
    public void testSetClientReceivedNoClientSpan() {
        state.setCurrentClientSpan(null);

        clientTracer.setClientReceived();

        verifyNoMoreInteractions(mockSpan, mockCollector, mockSampler);
    }

    @Test
    public void testSetClientReceived() {
        Span clientRecv = new Span().setTimestamp(100L);
        state.setCurrentClientSpan(clientRecv);

        clientTracer.setClientReceived();

        final Annotation expectedAnnotation = new Annotation();
        expectedAnnotation.setHost(state.getClientEndpoint());
        expectedAnnotation.setValue(zipkinCoreConstants.CLIENT_RECV);
        expectedAnnotation.setTimestamp(CURRENT_TIME_MICROSECONDS);

        assertNull(state.getCurrentClientSpan());
        assertEquals(state.getServerEndpoint(), state.getClientEndpoint());

        verify(mockCollector).collect(clientRecv);
        verifyNoMoreInteractions(mockCollector, mockSampler);

        assertEquals(CURRENT_TIME_MICROSECONDS - clientRecv.timestamp, clientRecv.duration);
        assertEquals(expectedAnnotation, clientRecv.annotations.get(0));
    }

    @Test
    public void testStartNewSpanSampleFalse() {
        state.setCurrentServerSpan(ServerSpan.NOT_SAMPLED);

        assertNull(clientTracer.startNewSpan(REQUEST_NAME));

        verifyNoMoreInteractions(mockSpan, mockCollector, mockSampler);
    }

    @Test
    public void testStartNewSpanSampleNullNotPartOfExistingSpan() {
        state.setCurrentServerSpan(ServerSpan.create(null));

        when(mockRandom.nextLong()).thenReturn(TRACE_ID);
        when(mockSampler.isSampled(TRACE_ID)).thenReturn(true);

        final SpanId newSpanId = clientTracer.startNewSpan(REQUEST_NAME);
        assertNotNull(newSpanId);
        assertEquals(TRACE_ID, newSpanId.getTraceId());
        assertEquals(TRACE_ID, newSpanId.getSpanId());
        assertNull(newSpanId.getParentSpanId());

        assertEquals(
                new Span().setTrace_id(TRACE_ID).setId(TRACE_ID).setName(REQUEST_NAME),
                state.getCurrentClientSpan()
        );

        verify(mockSampler).isSampled(TRACE_ID);

        verifyNoMoreInteractions(mockCollector, mockSampler);
    }

    @Test
    public void testStartNewSpanSampleTrueNotPartOfExistingSpan() {
        state.setCurrentServerSpan(ServerSpan.create(true));

        when(mockRandom.nextLong()).thenReturn(TRACE_ID);

        final SpanId newSpanId = clientTracer.startNewSpan(REQUEST_NAME);
        assertNotNull(newSpanId);
        assertEquals(TRACE_ID, newSpanId.getTraceId());
        assertEquals(TRACE_ID, newSpanId.getSpanId());
        assertNull(newSpanId.getParentSpanId());

        assertEquals(
                new Span().setTrace_id(TRACE_ID).setId(TRACE_ID).setName(REQUEST_NAME),
                state.getCurrentClientSpan()
        );

        verifyNoMoreInteractions(mockCollector, mockSampler);
    }

    @Test
    public void testStartNewSpanSampleTruePartOfExistingSpan() {
        final ServerSpan parentSpan = ServerSpan.create(PARENT_TRACE_ID, PARENT_SPAN_ID, null, "name");
        state.setCurrentServerSpan(parentSpan);
        when(mockRandom.nextLong()).thenReturn(1L);

        final SpanId newSpanId = clientTracer.startNewSpan(REQUEST_NAME);
        assertNotNull(newSpanId);
        assertEquals(PARENT_TRACE_ID, newSpanId.getTraceId());
        assertEquals(1l, newSpanId.getSpanId());
        assertEquals(Long.valueOf(PARENT_SPAN_ID), newSpanId.getParentSpanId());

        assertEquals(
                new Span().setTrace_id(PARENT_TRACE_ID).setId(1).setParent_id(PARENT_SPAN_ID).setName(REQUEST_NAME),
                state.getCurrentClientSpan()
        );

        verifyNoMoreInteractions(mockCollector, mockSampler);
    }

    @Test
    public void testSamplerFalse() {
        state.setCurrentServerSpan(ServerSpan.create(null, null));
        when(mockSampler.isSampled(TRACE_ID)).thenReturn(false);
        when(mockRandom.nextLong()).thenReturn(TRACE_ID);

        assertNull(clientTracer.startNewSpan(REQUEST_NAME));

        verify(mockSampler).isSampled(TRACE_ID);

        assertNull(state.getCurrentClientSpan());
        assertEquals(state.getServerEndpoint(), state.getClientEndpoint());

        verifyNoMoreInteractions(mockSampler, mockCollector);
    }
}
