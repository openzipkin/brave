package com.github.kristofa.brave.http;

import com.github.kristofa.brave.ClientResponseAdapter;

public class HttpClientResponseAdapter extends HttpResponseAdapter<HttpResponse>
    implements ClientResponseAdapter {

    public static FactoryBuilder factoryBuilder() {
        return new FactoryBuilder();
    }

    public static final class FactoryBuilder
        extends HttpResponseAdapter.FactoryBuilder<HttpClientResponseAdapter.FactoryBuilder> {

        public <R extends HttpResponse> ClientResponseAdapter.Factory<R> build(
            Class<? extends R> requestType) {
            return new HttpClientResponseAdapter.Factory(this, requestType);
        }

        FactoryBuilder() { // intentionally hidden
        }
    }

    static final class Factory<R extends HttpResponse>
        extends HttpResponseAdapter.Factory<R, ClientResponseAdapter>
        implements ClientResponseAdapter.Factory<R> {

        Factory(HttpClientResponseAdapter.FactoryBuilder builder, Class<R> requestType) {
            super(builder, requestType);
        }

        @Override public ClientResponseAdapter create(R request) {
            return new HttpClientResponseAdapter(this, request);
        }
    }

    /**
     * @deprecated please use {@link #factoryBuilder()}
     */
    @Deprecated
    public HttpClientResponseAdapter(HttpResponse response) {
        this((Factory) factoryBuilder().build(HttpResponse.class), response);
    }

    HttpClientResponseAdapter(Factory factory, HttpResponse response) { // intentionally hidden
        super(factory, response);
    }
}
