package com.github.kristofa.brave.http;

import com.github.kristofa.brave.ClientRequestAdapter;
import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.internal.Nullable;
import com.github.kristofa.brave.Propagation;
import com.twitter.zipkin.gen.Endpoint;

import java.util.Collection;
import java.util.Collections;
import zipkin.TraceKeys;

import static com.github.kristofa.brave.Propagation.KeyFactory.STRING;

public class HttpClientRequestAdapter implements ClientRequestAdapter {
    static final Propagation.Injector<HttpClientRequest> INJECTOR =
        Propagation.Factory.B3.create(STRING).injector(HttpClientRequest::addHeader);
    private final HttpClientRequest request;
    private final SpanNameProvider spanNameProvider;

    public HttpClientRequestAdapter(HttpClientRequest request, SpanNameProvider spanNameProvider) {
        this.request = request;
        this.spanNameProvider = spanNameProvider;
    }

    @Override
    public String getSpanName() {
        return spanNameProvider.spanName(request);
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated
    @Override
    public void addSpanIdToRequest(@Nullable SpanId spanId) {
        // adapting as opposed to throwing as this a public type some may have extended.
        INJECTOR.injectSpanId(spanId, request);
    }

    @Override
    public Collection<KeyValueAnnotation> requestAnnotations() {
        return Collections.singleton(KeyValueAnnotation.create(
                TraceKeys.HTTP_URL, request.getUri().toString()));
    }

    @Override
    public Endpoint serverAddress() {
        return null;
    }
}
