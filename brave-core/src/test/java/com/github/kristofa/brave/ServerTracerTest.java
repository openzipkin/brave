package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.AnnotationType;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.zipkinCoreConstants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(AnnotationSubmitter.class)
public class ServerTracerTest {

    private final static long CURRENT_TIME_MICROSECONDS = System.currentTimeMillis() * 1000;
    private final static long TRACE_ID = 1l;
    private final static long SPAN_ID = 2l;
    private final static Long PARENT_SPANID = 3l;
    private final static String SPAN_NAME = "span name";

    private ServerTracer serverTracer;
    private ServerSpanState mockServerSpanState;
    private SpanCollector mockSpanCollector;
    private ServerSpan mockServerSpan;
    private Span mockSpan;
    private Endpoint mockEndpoint;
    private Random mockRandom;
    private Sampler mockSampler;

    @Before
    public void setup() {
        mockServerSpanState = mock(ServerSpanState.class);
        mockSpanCollector = mock(SpanCollector.class);
        mockSpan = mock(Span.class);
        mockServerSpan = mock(ServerSpan.class);

        mockEndpoint = new Endpoint();
        mockRandom = mock(Random.class);
        mockSampler = mock(Sampler.class);

        PowerMockito.mockStatic(System.class);
        PowerMockito.when(System.currentTimeMillis()).thenReturn(CURRENT_TIME_MICROSECONDS / 1000);
        serverTracer = ServerTracer.builder()
            .state(mockServerSpanState)
            .randomGenerator(mockRandom)
            .spanCollector(mockSpanCollector)
            .traceSampler(mockSampler).build();
    }

    @Test
    public void testClearCurrentSpan() {
        serverTracer.clearCurrentSpan();
        verify(mockServerSpanState).setCurrentServerSpan(null);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

    @Test
    public void testSetStateCurrentTrace() {
        serverTracer.setStateCurrentTrace(TRACE_ID, SPAN_ID, PARENT_SPANID, SPAN_NAME);
        final ServerSpan expectedServerSpan = ServerSpan.create(TRACE_ID, SPAN_ID, PARENT_SPANID, SPAN_NAME);
        verify(mockServerSpanState).setCurrentServerSpan(expectedServerSpan);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

    @Test
    public void testSetStateNoTracing() {
        serverTracer.setStateNoTracing();
        final ServerSpan expectedServerSpan = ServerSpan.create(false);
        verify(mockServerSpanState).setCurrentServerSpan(expectedServerSpan);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

    @Test
    public void testSetStateUnknownSamplerTrue() {

        when(mockRandom.nextLong()).thenReturn(TRACE_ID);
        when(mockSampler.isSampled(TRACE_ID)).thenReturn(true);

        serverTracer.setStateUnknown(SPAN_NAME);
        final ServerSpan expectedServerSpan = ServerSpan.create(TRACE_ID, TRACE_ID, null, SPAN_NAME);

        final InOrder inOrder = inOrder(mockSampler, mockRandom, mockServerSpanState);

        inOrder.verify(mockRandom).nextLong();
        inOrder.verify(mockSampler).isSampled(TRACE_ID);
        inOrder.verify(mockServerSpanState).setCurrentServerSpan(expectedServerSpan);

        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockRandom);
    }

    @Test
    public void testSetStateUnknownSamplerFalse() {

        when(mockRandom.nextLong()).thenReturn(TRACE_ID);
        when(mockSampler.isSampled(TRACE_ID)).thenReturn(false);

        final ServerSpan expectedServerSpan = ServerSpan.create(false);

        serverTracer.setStateUnknown(SPAN_NAME);

        final InOrder inOrder = inOrder(mockSampler, mockRandom, mockServerSpanState);

        inOrder.verify(mockRandom).nextLong();
        inOrder.verify(mockSampler).isSampled(TRACE_ID);
        inOrder.verify(mockServerSpanState).setCurrentServerSpan(expectedServerSpan);

        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockRandom);
    }

    @Test
    public void testSetServerReceivedNoServerSpan() {

        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpan.getSpan()).thenReturn(null);
        serverTracer.setServerReceived();
        verify(mockServerSpanState).getCurrentServerSpan();
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

    @Test
    public void testSetServerReceived() {
        Span serverRecv = new Span();
        when(mockServerSpan.getSpan()).thenReturn(serverRecv);
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpanState.getServerEndpoint()).thenReturn(mockEndpoint);
        serverTracer.setServerReceived();

        final Annotation expectedAnnotation = new Annotation();
        expectedAnnotation.setHost(mockEndpoint);
        expectedAnnotation.setValue(zipkinCoreConstants.SERVER_RECV);
        expectedAnnotation.setTimestamp(CURRENT_TIME_MICROSECONDS);

        verify(mockServerSpanState).getCurrentServerSpan();
        verify(mockServerSpanState).getServerEndpoint();

        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);

        assertEquals(CURRENT_TIME_MICROSECONDS, serverRecv.timestamp);
        assertEquals(expectedAnnotation, serverRecv.annotations.get(0));
    }

    @Test
    public void testSetServerReceivedSentClientAddress() {
        Span serverRecv = new Span();
        when(mockServerSpan.getSpan()).thenReturn(serverRecv);
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpanState.getServerEndpoint()).thenReturn(mockEndpoint);
        serverTracer.setServerReceived(1 << 24 | 2 << 16 | 3 << 8 | 4, 9999, "foobar");

        final Annotation expectedAnnotation = new Annotation();
        expectedAnnotation.setHost(mockEndpoint);
        expectedAnnotation.setValue(zipkinCoreConstants.SERVER_RECV);
        expectedAnnotation.setTimestamp(CURRENT_TIME_MICROSECONDS);

        verify(mockServerSpanState, times(2)).getCurrentServerSpan();
        verify(mockServerSpanState).getServerEndpoint();

        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);

        assertEquals(CURRENT_TIME_MICROSECONDS, serverRecv.timestamp);
        assertEquals(expectedAnnotation, serverRecv.annotations.get(0));

        BinaryAnnotation serverAddress = new BinaryAnnotation()
            .setKey(zipkinCoreConstants.CLIENT_ADDR)
            .setValue(new byte[]{1})
            .setAnnotation_type(AnnotationType.BOOL)
            .setHost(new Endpoint()
                .setService_name("foobar")
                .setIpv4(1 << 24 | 2 << 16 | 3 << 8 | 4)
                .setPort((short) 9999));
        assertEquals(serverAddress, serverRecv.binary_annotations.get(0));
    }

    @Test
    public void testSetServerReceivedSentClientAddress_noServiceName() {
        Span serverRecv = new Span();
        when(mockServerSpan.getSpan()).thenReturn(serverRecv);
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpanState.getServerEndpoint()).thenReturn(mockEndpoint);
        serverTracer.setServerReceived(1 << 24 | 2 << 16 | 3 << 8 | 4, 9999, null);

        assertEquals("unknown", serverRecv.binary_annotations.get(0).getHost().getService_name());
    }

    @Test
    public void testSetServerSendShouldNoServerSpan() {

        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpan.getSpan()).thenReturn(null);
        serverTracer.setServerSend();
        verify(mockServerSpanState).getCurrentServerSpan();
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockSpan);
    }

    @Test
    public void testSetServerSend() {
        Span serverSend = new Span().setTimestamp(100L);
        when(mockServerSpan.getSpan()).thenReturn(serverSend);
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpanState.getServerEndpoint()).thenReturn(mockEndpoint);
        serverTracer.setServerSend();

        final Annotation expectedAnnotation = new Annotation();
        expectedAnnotation.setHost(mockEndpoint);
        expectedAnnotation.setValue(zipkinCoreConstants.SERVER_SEND);
        expectedAnnotation.setTimestamp(CURRENT_TIME_MICROSECONDS);

        verify(mockServerSpanState).getCurrentServerSpan();
        verify(mockServerSpanState).getServerEndpoint();

        verify(mockSpanCollector).collect(serverSend);
        verify(mockServerSpanState).setCurrentServerSpan(null);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);

        assertEquals(CURRENT_TIME_MICROSECONDS - serverSend.timestamp, serverSend.duration);
        assertEquals(expectedAnnotation, serverSend.annotations.get(0));
    }

}
