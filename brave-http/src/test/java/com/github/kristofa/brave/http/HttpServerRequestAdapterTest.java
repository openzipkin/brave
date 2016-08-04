package com.github.kristofa.brave.http;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static junit.framework.Assert.assertNull;

import java.net.URI;
import java.util.Collection;
import java.util.Iterator;

import org.junit.Before;
import org.junit.Test;

import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.TraceData;

import zipkin.TraceKeys;

public class HttpServerRequestAdapterTest {

    private final static String TRACE_ID = "7a842183262a6c62";
    private final static String SPAN_ID = "bf38b90488a1e481";
    private final static String PARENT_SPAN_ID = "8000000000000000";

    private HttpServerRequestAdapter adapter;
    private HttpServerRequest serverRequest;
    private SpanNameProvider spanNameProvider;

    @Before
    public void setup() {
        serverRequest = mock(HttpServerRequest.class);
        spanNameProvider = mock(SpanNameProvider.class);
        adapter = new HttpServerRequestAdapter(serverRequest, spanNameProvider);
    }

    @Test
    public void getTraceDataNoSampledHeader() {
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.Sampled.getName())).thenReturn(null);
        TraceData traceData = adapter.getTraceData();
        assertNotNull(traceData);
        assertNull(traceData.getSample());
        assertNull(traceData.getSpanId());
    }

    @Test
    public void getTraceDataSampledFalse() {
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.Sampled.getName())).thenReturn("false");
        TraceData traceData = adapter.getTraceData();
        assertNotNull(traceData);
        assertFalse(traceData.getSample());
        assertNull(traceData.getSpanId());
    }

    @Test
    public void getTraceDataSampledFalseUpperCase() {
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.Sampled.getName())).thenReturn("FALSE");
        TraceData traceData = adapter.getTraceData();
        assertNotNull(traceData);
        assertFalse(traceData.getSample());
        assertNull(traceData.getSpanId());
    }

    /**
     * This is according to the zipkin 'spec'.
     */
    @Test
    public void getTraceDataSampledZero() {
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.Sampled.getName())).thenReturn("0");
        TraceData traceData = adapter.getTraceData();
        assertNotNull(traceData);
        assertFalse(traceData.getSample());
        assertNull(traceData.getSpanId());
    }

    @Test
    public void getTraceDataSampledTrueNoOtherTraceHeaders() {
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.Sampled.getName())).thenReturn("true");
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.TraceId.getName())).thenReturn(null);
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.SpanId.getName())).thenReturn(null);
        TraceData traceData = adapter.getTraceData();
        assertNotNull(traceData);
        assertNull(traceData.getSample());
        assertNull(traceData.getSpanId());
    }

    @Test
    public void getTraceDataSampledTrueNoParentId() {
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.Sampled.getName())).thenReturn("true");
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.TraceId.getName())).thenReturn(TRACE_ID);
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.SpanId.getName())).thenReturn(SPAN_ID);
        TraceData traceData = adapter.getTraceData();
        assertNotNull(traceData);
        assertTrue(traceData.getSample());
        SpanId spanId = traceData.getSpanId();
        assertNotNull(spanId);
        assertEquals(IdConversion.convertToLong(TRACE_ID), spanId.traceId);
        assertEquals(IdConversion.convertToLong(SPAN_ID), spanId.spanId);
        assertNull(spanId.nullableParentId());
    }

    /**
     * This is according to the zipkin 'spec'.
     */
    @Test
    public void getTraceDataSampledOneNoParentId() {
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.Sampled.getName())).thenReturn("1");
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.TraceId.getName())).thenReturn(TRACE_ID);
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.SpanId.getName())).thenReturn(SPAN_ID);
        TraceData traceData = adapter.getTraceData();
        assertNotNull(traceData);
        assertTrue(traceData.getSample());
        SpanId spanId = traceData.getSpanId();
        assertNotNull(spanId);
        assertEquals(IdConversion.convertToLong(TRACE_ID), spanId.traceId);
        assertEquals(IdConversion.convertToLong(SPAN_ID), spanId.spanId);
        assertNull(spanId.nullableParentId());
    }

    @Test
    public void getTraceDataSampledTrueWithParentId() {
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.Sampled.getName())).thenReturn("true");
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.TraceId.getName())).thenReturn(TRACE_ID);
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.SpanId.getName())).thenReturn(SPAN_ID);
        when(serverRequest.getHttpHeaderValue(BraveHttpHeaders.ParentSpanId.getName())).thenReturn(PARENT_SPAN_ID);

        TraceData traceData = adapter.getTraceData();
        assertNotNull(traceData);
        assertTrue(traceData.getSample());
        SpanId spanId = traceData.getSpanId();
        assertNotNull(spanId);
        assertEquals(IdConversion.convertToLong(TRACE_ID), spanId.traceId);
        assertEquals(IdConversion.convertToLong(SPAN_ID), spanId.spanId);
        assertEquals(IdConversion.convertToLong(PARENT_SPAN_ID), spanId.parentId);
    }

    @Test
    public void fullUriAnnotation() throws Exception {
        when(serverRequest.getUri()).thenReturn(new URI("http://youruri.com/a/b?myquery=you"));
        when(serverRequest.getHttpMethod()).thenReturn("GET");

        Collection<KeyValueAnnotation> annotations = adapter.requestAnnotations();
        assertEquals(2, annotations.size());

        Iterator<KeyValueAnnotation> iterator = annotations.iterator();

        KeyValueAnnotation uriAnnotation = iterator.next();
        assertEquals(TraceKeys.HTTP_URL, uriAnnotation.getKey());
        assertEquals("http://youruri.com/a/b?myquery=you", uriAnnotation.getValue());

        KeyValueAnnotation methodAnnotation = iterator.next();
        assertEquals(TraceKeys.HTTP_METHOD, methodAnnotation.getKey());
        assertEquals("GET", methodAnnotation.getValue());
    }


}
