package com.github.kristofa.brave.jersey;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientRequestAdapter;
import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientResponseAdapter;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.TagExtractor;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.HttpClientResponseAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.ClientFilter;
import javax.inject.Inject;
import javax.inject.Singleton;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * This filter creates or forwards trace headers and sends cs and cr annotations. Usage:
 *
 * <pre>
 * Client client = Client.create()
 * client.addFilter(JerseyClientTraceFilter.create(brave));
 * </pre>
 */
@Singleton
public class JerseyClientTraceFilter extends ClientFilter {

    /** Creates a tracing filter with defaults. Use {@link #builder(Brave)} to customize. */
    public static JerseyClientTraceFilter create(Brave brave) {
        return new Builder(brave).build();
    }

    public static Builder builder(Brave brave) {
        return new Builder(brave);
    }

    public static final class Builder implements TagExtractor.Config<Builder> {
        final Brave brave;
        final HttpClientRequestAdapter.FactoryBuilder requestFactoryBuilder
            = HttpClientRequestAdapter.factoryBuilder();
        final HttpClientResponseAdapter.FactoryBuilder responseFactoryBuilder
            = HttpClientResponseAdapter.factoryBuilder();

        Builder(Brave brave) { // intentionally hidden
            this.brave = checkNotNull(brave, "brave");
        }

        public Builder spanNameProvider(SpanNameProvider spanNameProvider) {
            requestFactoryBuilder.spanNameProvider(spanNameProvider);
            return this;
        }

        @Override public Builder addKey(String key) {
            requestFactoryBuilder.addKey(key);
            responseFactoryBuilder.addKey(key);
            return this;
        }

        @Override
        public Builder addValueParserFactory(TagExtractor.ValueParserFactory factory) {
            requestFactoryBuilder.addValueParserFactory(factory);
            responseFactoryBuilder.addValueParserFactory(factory);
            return this;
        }

        public JerseyClientTraceFilter build() {
            return new JerseyClientTraceFilter(this);
        }
    }

    private final ClientRequestInterceptor requestInterceptor;
    private final ClientResponseInterceptor responseInterceptor;
    private final ClientRequestAdapter.Factory<JerseyHttpRequest> requestAdapterFactory;
    private final ClientResponseAdapter.Factory<JerseyHttpResponse> responseAdapterFactory;

    JerseyClientTraceFilter(Builder b) { // intentionally hidden
        this.requestInterceptor = b.brave.clientRequestInterceptor();
        this.responseInterceptor = b.brave.clientResponseInterceptor();
        this.requestAdapterFactory = b.requestFactoryBuilder.build(JerseyHttpRequest.class);
        this.responseAdapterFactory = b.responseFactoryBuilder.build(JerseyHttpResponse.class);
    }

    @Inject // internal dependency-injection constructor
    JerseyClientTraceFilter(SpanNameProvider spanNameProvider, Brave brave) {
        this(builder(brave).spanNameProvider(spanNameProvider));
    }

    /**
     * @deprecated please use {@link #create(Brave)} or {@link #builder(Brave)}
     */
    @Deprecated
    public JerseyClientTraceFilter(SpanNameProvider spanNameProvider, ClientRequestInterceptor requestInterceptor, ClientResponseInterceptor responseInterceptor) {
        this.requestInterceptor = requestInterceptor;
        this.responseInterceptor = responseInterceptor;
        this.requestAdapterFactory = HttpClientRequestAdapter.factoryBuilder()
            .spanNameProvider(spanNameProvider)
            .build(JerseyHttpRequest.class);
        this.responseAdapterFactory = HttpClientResponseAdapter.factoryBuilder()
            .build(JerseyHttpResponse.class);
    }

    @Override
    public ClientResponse handle(final ClientRequest clientRequest) throws ClientHandlerException {
        ClientRequestAdapter requestAdapter =
            requestAdapterFactory.create(new JerseyHttpRequest(clientRequest));
        requestInterceptor.handle(requestAdapter);
        final ClientResponse clientResponse = getNext().handle(clientRequest);
        ClientResponseAdapter responseAdapter =
            responseAdapterFactory.create(new JerseyHttpResponse(clientResponse));
        responseInterceptor.handle(responseAdapter);
        return clientResponse;
    }
}
