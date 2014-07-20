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
        if (spanId != null) {
            LOGGER.debug("Will trace request. Span Id returned from ClientTracer: {}", spanId);
            clientRequestAdapter.addHeader(BraveHttpHeaders.Sampled.getName(), TRUE);
            clientRequestAdapter.addHeader(BraveHttpHeaders.TraceId.getName(), Long.toHexString(spanId.getTraceId()));
            clientRequestAdapter.addHeader(BraveHttpHeaders.SpanId.getName(), Long.toHexString(spanId.getSpanId()));
            if (spanId.getParentSpanId() != null) {
                clientRequestAdapter.addHeader(BraveHttpHeaders.ParentSpanId.getName(), Long.toHexString(spanId.getParentSpanId()));
            }
        } else {
            LOGGER.debug("Will not trace request.");
            clientRequestAdapter.addHeader(BraveHttpHeaders.Sampled.getName(), FALSE);
        }
    }
}
