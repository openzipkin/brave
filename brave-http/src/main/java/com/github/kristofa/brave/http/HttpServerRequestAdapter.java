package com.github.kristofa.brave.http;

import static com.github.kristofa.brave.IdConversion.convertToLong;

import java.util.Arrays;
import java.util.Collection;

import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.ServerRequestAdapter;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.TraceData;

import zipkin.TraceKeys;

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
        KeyValueAnnotation uriAnnotation = KeyValueAnnotation.create(
                TraceKeys.HTTP_URL, serverRequest.getUri().toString());

        KeyValueAnnotation methodAnnotation =
                KeyValueAnnotation.create(TraceKeys.HTTP_METHOD, serverRequest.getHttpMethod().toString());

        return Arrays.asList(uriAnnotation, methodAnnotation);
    }

    private SpanId getSpanId(String traceId, String spanId, String parentSpanId) {
        return SpanId.builder()
            .traceId(convertToLong(traceId))
            .spanId(convertToLong(spanId))
            .parentId(parentSpanId == null ? null : convertToLong(parentSpanId)).build();
   }
}
