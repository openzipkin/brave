package com.github.kristofa.brave.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.SpanId;

public class ClientRequestHeaders {

    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private final static Logger LOGGER = LoggerFactory.getLogger(ClientRequestHeaders.class);

    public static void addTracingHeaders(final ClientRequestAdapter clientRequestAdapter, final SpanId spanId,
        final String spanName) {
        if (spanId != null) {
            LOGGER.debug("Will trace request. Span Id returned from ClientTracer: {}", spanId);
            clientRequestAdapter.addHeader(BraveHttpHeaders.Sampled.getName(), TRUE);
            clientRequestAdapter.addHeader(BraveHttpHeaders.TraceId.getName(), IdConversion.convertToString(spanId.getTraceId()));
            clientRequestAdapter.addHeader(BraveHttpHeaders.SpanId.getName(), IdConversion.convertToString(spanId.getSpanId()));
            if (spanId.getParentSpanId() != null) {
                clientRequestAdapter.addHeader(BraveHttpHeaders.ParentSpanId.getName(),
                		IdConversion.convertToString(spanId.getParentSpanId()));
            }
            if (spanName != null) {
                clientRequestAdapter.addHeader(BraveHttpHeaders.SpanName.getName(), spanName);
            }
        } else {
            LOGGER.debug("Will not trace request.");
            clientRequestAdapter.addHeader(BraveHttpHeaders.Sampled.getName(), FALSE);
        }
    }
}
