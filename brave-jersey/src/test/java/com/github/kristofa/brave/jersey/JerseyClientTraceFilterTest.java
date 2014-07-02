package com.github.kristofa.brave.jersey;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.SpanId;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.core.util.StringKeyObjectValueIgnoreCaseMultivaluedMap;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.ws.rs.core.MultivaluedMap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class JerseyClientTraceFilterTest {

    @Mock
    ClientTracer clientTracer;
    @Mock
    ClientRequest clientRequest;
    @InjectMocks
    JerseyClientTraceFilter jerseyClientTraceFilter = new JerseyClientTraceFilter(clientTracer);

    @Test
    public void shouldAddAllFields() {
        when(clientRequest.getHeaders()).thenReturn(new StringKeyObjectValueIgnoreCaseMultivaluedMap());
        jerseyClientTraceFilter.addTracingHeaders(clientRequest, mockSpan(123L, 456L, 789L));
        MultivaluedMap<String, Object> headers = clientRequest.getHeaders();
        Assert.assertEquals("123", headers.getFirst(BraveHttpHeaders.TraceId.getName()));
        Assert.assertEquals("456", headers.getFirst(BraveHttpHeaders.SpanId.getName()));
        Assert.assertEquals("789", headers.getFirst(BraveHttpHeaders.ParentSpanId.getName()));
        Assert.assertEquals("true", headers.getFirst(BraveHttpHeaders.Sampled.getName()));
    }

    @Test
    public void shouldNotIncludeMissingParentId() {
        when(clientRequest.getHeaders()).thenReturn(new StringKeyObjectValueIgnoreCaseMultivaluedMap());
        jerseyClientTraceFilter.addTracingHeaders(clientRequest, mockSpan(123L, 456L, null));
        MultivaluedMap<String, Object> headers = clientRequest.getHeaders();
        Assert.assertEquals("123", headers.getFirst(BraveHttpHeaders.TraceId.getName()));
        Assert.assertEquals("456", headers.getFirst(BraveHttpHeaders.SpanId.getName()));
        Assert.assertEquals(null, headers.getFirst(BraveHttpHeaders.ParentSpanId.getName()));
        Assert.assertEquals("true", headers.getFirst(BraveHttpHeaders.Sampled.getName()));
    }

    @Test
    public void shouldSetSampledToFalseIfNull() {
        when(clientRequest.getHeaders()).thenReturn(new StringKeyObjectValueIgnoreCaseMultivaluedMap());
        jerseyClientTraceFilter.addTracingHeaders(clientRequest, null);
        MultivaluedMap<String, Object> headers = clientRequest.getHeaders();
        Assert.assertEquals("false", headers.getFirst(BraveHttpHeaders.Sampled.getName()));
    }

    private SpanId mockSpan(long traceId, long spanId, Long parentSpanId) {
        SpanId mockedSpan = mock(SpanId.class);
        when(mockedSpan.getTraceId()).thenReturn(traceId);
        when(mockedSpan.getSpanId()).thenReturn(spanId);
        when(mockedSpan.getParentSpanId()).thenReturn(parentSpanId);
        return mockedSpan;
    }
}
