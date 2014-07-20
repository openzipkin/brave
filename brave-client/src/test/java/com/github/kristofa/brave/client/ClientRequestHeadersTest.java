package com.github.kristofa.brave.client;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.ClientRequestAdapter;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.SpanId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ClientRequestHeadersTest {

    @Mock
    ClientTracer clientTracer;
    @Mock
    ClientRequestAdapter clientRequest;

    @Test
    public void shouldAddAllFields() {
        ClientRequestHeaders.addTracingHeaders(clientRequest, mockSpan(123L, 456L, 789L));
        verify(clientRequest).addHeader(BraveHttpHeaders.TraceId.getName(), "7b");
        verify(clientRequest).addHeader(BraveHttpHeaders.SpanId.getName(), "1c8");
        verify(clientRequest).addHeader(BraveHttpHeaders.ParentSpanId.getName(), "315");
        verify(clientRequest).addHeader(BraveHttpHeaders.Sampled.getName(), "true");
        verifyNoMoreInteractions(clientRequest);
    }

    @Test
    public void shouldNotIncludeMissingParentId() {
        ClientRequestHeaders.addTracingHeaders(clientRequest, mockSpan(123L, 456L, null));
        verify(clientRequest).addHeader(BraveHttpHeaders.TraceId.getName(), "7b");
        verify(clientRequest).addHeader(BraveHttpHeaders.SpanId.getName(), "1c8");
        verify(clientRequest).addHeader(BraveHttpHeaders.Sampled.getName(), "true");
        verifyNoMoreInteractions(clientRequest);
    }

    @Test
    public void shouldSetSampledToFalseIfNull() {
        ClientRequestHeaders.addTracingHeaders(clientRequest, null);
        verify(clientRequest).addHeader(BraveHttpHeaders.Sampled.getName(), "false");
        verifyNoMoreInteractions(clientRequest);
    }

    private SpanId mockSpan(long traceId, long spanId, Long parentSpanId) {
        SpanId mockedSpan = mock(SpanId.class);
        when(mockedSpan.getTraceId()).thenReturn(traceId);
        when(mockedSpan.getSpanId()).thenReturn(spanId);
        when(mockedSpan.getParentSpanId()).thenReturn(parentSpanId);
        return mockedSpan;
    }
}
