package com.github.kristofa.brave.client;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.ClientRequestAdapter;
import com.github.kristofa.brave.SpanId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientRequestHeaders {
    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private final static Logger LOGGER = LoggerFactory.getLogger(ClientRequestHeaders.class);

    public static void addTracingHeaders(ClientRequestAdapter clientRequestAdapter, SpanId spanId) {
        addTracingHeaders(clientRequestAdapter, spanId, null);
    }

    public static void addTracingHeaders(ClientRequestAdapter clientRequestAdapter, SpanId spanId, String spanName) {
        if (spanId != null) {
            LOGGER.debug("Will trace request. Span Id returned from ClientTracer: {}", spanId);
            clientRequestAdapter.addHeader(BraveHttpHeaders.Sampled.getName(), TRUE);
            clientRequestAdapter.addHeader(BraveHttpHeaders.TraceId.getName(), Long.toString(spanId.getTraceId(), 16));
            clientRequestAdapter.addHeader(BraveHttpHeaders.SpanId.getName(), Long.toString(spanId.getSpanId(), 16));
            if (spanId.getParentSpanId() != null) {
                clientRequestAdapter.addHeader(BraveHttpHeaders.ParentSpanId.getName(), Long.toString(spanId.getParentSpanId(), 16));
            }
            if(spanName != null) {
                clientRequestAdapter.addHeader(BraveHttpHeaders.SpanName.getName(), spanName);
            }
        } else {
            LOGGER.debug("Will not trace request.");
            clientRequestAdapter.addHeader(BraveHttpHeaders.Sampled.getName(), FALSE);
        }
    }
}
