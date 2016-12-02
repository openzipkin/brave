package com.github.kristofa.brave.http;

import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.ServerRequestAdapter;
import com.github.kristofa.brave.TraceData;
import com.github.kristofa.brave.Propagation;
import java.util.Collection;
import java.util.Collections;
import zipkin.TraceKeys;

import static com.github.kristofa.brave.Propagation.KeyFactory.STRING;

public class HttpServerRequestAdapter implements ServerRequestAdapter {
    static final Propagation.Extractor<HttpServerRequest> EXTRACTOR =
        Propagation.Factory.B3.create(STRING).extractor(HttpServerRequest::getHttpHeaderValue);
    private final HttpServerRequest request;
    private final SpanNameProvider spanNameProvider;

    public HttpServerRequestAdapter(HttpServerRequest request, SpanNameProvider spanNameProvider) {
        this.request = request;
        this.spanNameProvider = spanNameProvider;
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated
    @Override
    public TraceData getTraceData() {
        // adapting as opposed to throwing as this a public type some may have extended.
        return EXTRACTOR.extractTraceData(request);
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
}
