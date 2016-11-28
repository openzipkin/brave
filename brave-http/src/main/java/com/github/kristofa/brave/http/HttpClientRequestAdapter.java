package com.github.kristofa.brave.http;

import com.github.kristofa.brave.ClientRequestAdapter;
import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.internal.Nullable;
import com.twitter.zipkin.gen.Endpoint;

public class HttpClientRequestAdapter extends HttpRequestAdapter<HttpClientRequest>
    implements ClientRequestAdapter {

    public static FactoryBuilder factoryBuilder() {
        return new FactoryBuilder();
    }

    public static final class FactoryBuilder
        extends HttpRequestAdapter.FactoryBuilder<HttpClientRequestAdapter.FactoryBuilder> {

        public <R extends HttpClientRequest> ClientRequestAdapter.Factory<R> build(
            Class<? extends R> requestType) {
            return new HttpClientRequestAdapter.Factory(this, requestType);
        }

        FactoryBuilder() { // intentionally hidden
        }
    }

    static final class Factory<R extends HttpClientRequest>
        extends HttpRequestAdapter.Factory<R, ClientRequestAdapter>
        implements ClientRequestAdapter.Factory<R> {

        Factory(HttpClientRequestAdapter.FactoryBuilder builder, Class<R> requestType) {
            super(builder, requestType);
        }

        @Override public ClientRequestAdapter create(R request) {
            return new HttpClientRequestAdapter(this, request);
        }
    }

    /**
     * @deprecated please use {@link #factoryBuilder()}
     */
    @Deprecated
    public HttpClientRequestAdapter(HttpClientRequest request, SpanNameProvider spanNameProvider) {
        this((Factory)
                factoryBuilder().spanNameProvider(spanNameProvider).build(HttpClientRequest.class),
            request);
    }

    HttpClientRequestAdapter(Factory factory, HttpClientRequest request) { // intentionally hidden
        super(factory, request);
    }

    @Override
    public void addSpanIdToRequest(@Nullable SpanId spanId) {
        if (spanId == null) {
            request.addHeader(BraveHttpHeaders.Sampled.getName(), "0");
        } else {
            request.addHeader(BraveHttpHeaders.Sampled.getName(), "1");
            request.addHeader(BraveHttpHeaders.TraceId.getName(), spanId.traceIdString());
            request.addHeader(BraveHttpHeaders.SpanId.getName(), IdConversion.convertToString(spanId.spanId));
            if (spanId.nullableParentId() != null) {
                request.addHeader(BraveHttpHeaders.ParentSpanId.getName(), IdConversion.convertToString(spanId.parentId));
            }
        }
    }

    @Override
    public Endpoint serverAddress() {
        return null;
    }
}
