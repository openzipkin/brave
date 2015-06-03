package com.github.kristofa.brave.client;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.SpanId;

@RunWith(MockitoJUnitRunner.class)
public class ClientRequestHeadersTest {

    private static final String SPAN_NAME = "spanname";

    @Mock
    ClientTracer clientTracer;
    @Mock
    ClientRequestAdapter clientRequest;

    @Test
    public void shouldAddAllFields() {
        ClientRequestHeaders.addTracingHeaders(clientRequest, mockSpan(123L, 456L, 789L), SPAN_NAME);
        verify(clientRequest).addHeader(BraveHttpHeaders.TraceId.getName(), "7b");
        verify(clientRequest).addHeader(BraveHttpHeaders.SpanId.getName(), "1c8");
        verify(clientRequest).addHeader(BraveHttpHeaders.ParentSpanId.getName(), "315");
        verify(clientRequest).addHeader(BraveHttpHeaders.Sampled.getName(), "true");
        verify(clientRequest).addHeader(BraveHttpHeaders.SpanName.getName(), SPAN_NAME);
        verifyNoMoreInteractions(clientRequest);
    }

    @Test
    public void shouldNullSpanName() {
        ClientRequestHeaders.addTracingHeaders(clientRequest, mockSpan(123L, 456L, 789L), null);
        verify(clientRequest).addHeader(BraveHttpHeaders.TraceId.getName(), "7b");
        verify(clientRequest).addHeader(BraveHttpHeaders.SpanId.getName(), "1c8");
        verify(clientRequest).addHeader(BraveHttpHeaders.ParentSpanId.getName(), "315");
        verify(clientRequest).addHeader(BraveHttpHeaders.Sampled.getName(), "true");
        verifyNoMoreInteractions(clientRequest);
    }

    @Test
    public void shouldNotIncludeMissingParentId() {
        ClientRequestHeaders.addTracingHeaders(clientRequest, mockSpan(123L, 456L, null), SPAN_NAME);
        verify(clientRequest).addHeader(BraveHttpHeaders.TraceId.getName(), "7b");
        verify(clientRequest).addHeader(BraveHttpHeaders.SpanId.getName(), "1c8");
        verify(clientRequest).addHeader(BraveHttpHeaders.Sampled.getName(), "true");
        verify(clientRequest).addHeader(BraveHttpHeaders.SpanName.getName(), SPAN_NAME);
        verifyNoMoreInteractions(clientRequest);
    }

    @Test
    public void shouldSetSampledToFalseIfNull() {
        ClientRequestHeaders.addTracingHeaders(clientRequest, null, SPAN_NAME);
        verify(clientRequest).addHeader(BraveHttpHeaders.Sampled.getName(), "false");
        verifyNoMoreInteractions(clientRequest);
    }

    private SpanId mockSpan(final long traceId, final long spanId, final Long parentSpanId) {
        final SpanId mockedSpan = mock(SpanId.class);
        when(mockedSpan.getTraceId()).thenReturn(traceId);
        when(mockedSpan.getSpanId()).thenReturn(spanId);
        when(mockedSpan.getParentSpanId()).thenReturn(parentSpanId);
        return mockedSpan;
    }
}
