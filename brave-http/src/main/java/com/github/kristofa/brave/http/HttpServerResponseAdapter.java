package com.github.kristofa.brave.http;

import com.github.kristofa.brave.ServerResponseAdapter;

public class HttpServerResponseAdapter extends HttpResponseAdapter<HttpResponse>
    implements ServerResponseAdapter {

    public static FactoryBuilder factoryBuilder() {
        return new FactoryBuilder();
    }

    public static final class FactoryBuilder
        extends HttpResponseAdapter.FactoryBuilder<HttpServerResponseAdapter.FactoryBuilder> {

        public <R extends HttpResponse> ServerResponseAdapter.Factory<R> build(
            Class<? extends R> requestType) {
            return new HttpServerResponseAdapter.Factory(this, requestType);
        }

        FactoryBuilder() { // intentionally hidden
        }
    }

    static final class Factory<R extends HttpResponse>
        extends HttpResponseAdapter.Factory<R, ServerResponseAdapter>
        implements ServerResponseAdapter.Factory<R> {

        Factory(HttpServerResponseAdapter.FactoryBuilder builder, Class<R> requestType) {
            super(builder, requestType);
        }

        @Override public ServerResponseAdapter create(R request) {
            return new HttpServerResponseAdapter(this, request);
        }
    }

    /**
     * @deprecated please use {@link #factoryBuilder()}
     */
    @Deprecated
    public HttpServerResponseAdapter(HttpResponse response) {
        this((Factory) factoryBuilder().build(HttpResponse.class), response);
    }

    HttpServerResponseAdapter(Factory factory, HttpResponse response) { // intentionally hidden
        super(factory, response);
    }
}
