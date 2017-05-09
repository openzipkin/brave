package com.github.kristofa.brave.http;

import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.ServerRequestAdapter;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.TraceData;
import java.util.Collection;
import java.util.Collections;
import zipkin.TraceKeys;

import static com.github.kristofa.brave.IdConversion.convertToLong;

public class HttpServerRequestAdapter implements ServerRequestAdapter {
    private final HttpServerRequest request;
    private final SpanNameProvider spanNameProvider;

    public HttpServerRequestAdapter(HttpServerRequest request, SpanNameProvider spanNameProvider) {
        this.request = request;
        this.spanNameProvider = spanNameProvider;
    }

    @Override
    public TraceData getTraceData() {
        String sampled = request.getHttpHeaderValue(BraveHttpHeaders.Sampled.getName());
        String parentSpanId = request.getHttpHeaderValue(BraveHttpHeaders.ParentSpanId.getName());
        String traceId = request.getHttpHeaderValue(BraveHttpHeaders.TraceId.getName());
        String spanId = request.getHttpHeaderValue(BraveHttpHeaders.SpanId.getName());

        // Official sampled value is 1, though some old instrumentation send true
        Boolean parsedSampled = sampled != null
            ? sampled.equals("1") || sampled.equalsIgnoreCase("true")
            : null;

        if (traceId != null && spanId != null) {
            return TraceData.create(getSpanId(traceId, spanId, parentSpanId, parsedSampled));
        } else if (parsedSampled == null) {
            return TraceData.EMPTY;
        } else if (parsedSampled.booleanValue()) {
            // Invalid: The caller requests the trace to be sampled, but didn't pass IDs
            return TraceData.EMPTY;
        } else {
            return TraceData.NOT_SAMPLED;
        }
    }

    @Override
    public String getSpanName() {
        return spanNameProvider.spanName(request);
    }

    @Override
    public Collection<KeyValueAnnotation> requestAnnotations() {
        KeyValueAnnotation uriAnnotation = KeyValueAnnotation.create(
                TraceKeys.HTTP_URL, request.getUri().toString());
        return Collections.singleton(uriAnnotation);
    }

    static SpanId getSpanId(String traceId, String spanId, String parentSpanId, Boolean sampled) {
        return SpanId.builder()
            .traceIdHigh(traceId.length() == 32 ? convertToLong(traceId, 0) : 0)
            .traceId(convertToLong(traceId))
            .spanId(convertToLong(spanId))
            .sampled(sampled)
            .parentId(parentSpanId == null ? null : convertToLong(parentSpanId)).build();
   }
}
