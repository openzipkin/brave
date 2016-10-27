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

    private static final TraceData EMPTY_UNSAMPLED_TRACE = TraceData.builder().sample(false).build();
    private static final TraceData EMPTY_MAYBE_TRACE = TraceData.builder().build();

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
            if (sampled.equals("0") || sampled.equalsIgnoreCase("false")) {
                return EMPTY_UNSAMPLED_TRACE;
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
        return EMPTY_MAYBE_TRACE;
    }

    @Override
    public String getSpanName() {
        return spanNameProvider.spanName(serverRequest);
    }

    @Override
    public Collection<KeyValueAnnotation> requestAnnotations() {
        KeyValueAnnotation uriAnnotation = KeyValueAnnotation.create(
                TraceKeys.HTTP_URL, serverRequest.getUri().toString());
        return Collections.singleton(uriAnnotation);
    }

    private SpanId getSpanId(String traceId, String spanId, String parentSpanId) {
        return SpanId.builder()
            .traceIdHigh(traceId.length() == 32 ? convertToLong(traceId, 0) : 0)
            .traceId(convertToLong(traceId))
            .spanId(convertToLong(spanId))
            .parentId(parentSpanId == null ? null : convertToLong(parentSpanId)).build();
   }
}
