package com.github.kristofa.brave.http;

import com.github.kristofa.brave.*;

import java.util.Collection;
import java.util.Collections;


public class HttpServerRequestAdapter implements ServerRequestAdapter {

    private final HttpServerRequest serverRequest;
    private final SpanNameProvider spanNameProvider;

    public HttpServerRequestAdapter(HttpServerRequest serverRequest, SpanNameProvider spanNameProvider) {
        this.serverRequest = serverRequest;
        this.spanNameProvider = spanNameProvider;
    }

    @Override
    public TraceData getTraceData() {
        final String sampled = serverRequest.getHttpHeaderValue(BraveHttpHeaders.Sampled.getName());
        if (sampled != null) {
            if (sampled.equals("0") || sampled.toLowerCase().equals("false")) {
                return TraceData.builder().sample(false).build();
            } else {
                final String parentSpanId = serverRequest.getHttpHeaderValue(BraveHttpHeaders.ParentSpanId.getName());
                final String traceId = serverRequest.getHttpHeaderValue(BraveHttpHeaders.TraceId.getName());
                final String spanId = serverRequest.getHttpHeaderValue(BraveHttpHeaders.SpanId.getName());

                if (traceId != null && spanId != null) {
                    SpanId span = getSpanId(traceId, spanId, parentSpanId);
                    return TraceData.builder().sample(true).spanId(span).build();
                }
            }
        }
        return TraceData.builder().build();
    }

    @Override
    public String getSpanName() {
        return spanNameProvider.spanName(serverRequest);
    }

    @Override
    public Collection<KeyValueAnnotation> requestAnnotations() {
        return Collections.emptyList();
    }

    private SpanId getSpanId(String traceId, String spanId, String parentSpanId) {
        if (parentSpanId != null)
        {
            return SpanId.create(IdConversion.convertToLong(traceId), IdConversion.convertToLong(spanId), IdConversion.convertToLong(parentSpanId));
        }
        return SpanId.create(IdConversion.convertToLong(traceId), IdConversion.convertToLong(spanId), null);
    }
}
