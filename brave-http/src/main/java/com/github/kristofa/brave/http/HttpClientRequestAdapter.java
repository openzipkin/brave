package com.github.kristofa.brave.http;

import com.github.kristofa.brave.ClientRequestAdapter;
import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.internal.Nullable;
import com.twitter.zipkin.gen.Endpoint;
import zipkin.TraceKeys;

import java.util.Collection;
import java.util.Collections;

public class HttpClientRequestAdapter implements ClientRequestAdapter {

    private final HttpClientRequest clientRequest;
    private final SpanNameProvider spanNameProvider;

    public HttpClientRequestAdapter(HttpClientRequest clientRequest, SpanNameProvider spanNameProvider) {
        this.clientRequest = clientRequest;
        this.spanNameProvider = spanNameProvider;
    }

    @Override
    public String getSpanName() {
        return spanNameProvider.spanName(clientRequest);
    }

    @Override
    public void addSpanIdToRequest(@Nullable SpanId spanId) {
        if (spanId == null) {
            clientRequest().addHeader(BraveHttpHeaders.Sampled.getName(), "0");
        } else {
            clientRequest().addHeader(BraveHttpHeaders.Sampled.getName(), "1");
            clientRequest().addHeader(BraveHttpHeaders.TraceId.getName(),
                    IdConversion.convertToString(spanId.traceId));
            clientRequest().addHeader(BraveHttpHeaders.SpanId.getName(),
                    IdConversion.convertToString(spanId.spanId));
            if (spanId.nullableParentId() != null) {
                clientRequest().addHeader(BraveHttpHeaders.ParentSpanId.getName(),
                        IdConversion.convertToString(spanId.parentId));
            }
        }
    }

    @Override
    public Collection<KeyValueAnnotation> requestAnnotations() {
        return Collections.singleton(KeyValueAnnotation.create(
                TraceKeys.HTTP_URL, clientRequest().getUri().toString()));
    }

    @Override
    public Endpoint serverAddress() {
        return null;
    }

    protected final HttpClientRequest clientRequest() {
        return clientRequest;
    }

}
