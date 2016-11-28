package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientRequestAdapter;
import com.github.kristofa.brave.TagExtractor;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Intercepts JAX-RS 2 client requests and adds or forwards tracing information in the header.
 * Also sends cs annotations.
 */
@Provider
@Priority(0)
public class BraveClientRequestFilter implements ClientRequestFilter {

    /** Creates a tracing filter with defaults. Use {@link #builder(Brave)} to customize. */
    public static BraveClientRequestFilter create(Brave brave) {
        return new Builder(brave).build();
    }

    public static Builder builder(Brave brave) {
        return new Builder(brave);
    }

    public static final class Builder implements TagExtractor.Config<Builder> {
        final Brave brave;
        final HttpClientRequestAdapter.FactoryBuilder adapterFactoryBuilder
            = HttpClientRequestAdapter.factoryBuilder();

        Builder(Brave brave) { // intentionally hidden
            this.brave = checkNotNull(brave, "brave");
        }

        public Builder spanNameProvider(SpanNameProvider spanNameProvider) {
            adapterFactoryBuilder.spanNameProvider(spanNameProvider);
            return this;
        }

        @Override public Builder addKey(String key) {
            adapterFactoryBuilder.addKey(key);
            return this;
        }

        @Override
        public Builder addValueParserFactory(TagExtractor.ValueParserFactory factory) {
            adapterFactoryBuilder.addValueParserFactory(factory);
            return this;
        }

        public BraveClientRequestFilter build() {
            return new BraveClientRequestFilter(this);
        }
    }

    private final ClientRequestInterceptor interceptor;
    private final ClientRequestAdapter.Factory<JaxRs2HttpClientRequest> adapterFactory;

    BraveClientRequestFilter(Builder b) { // intentionally hidden
        this.interceptor = b.brave.clientRequestInterceptor();
        this.adapterFactory = b.adapterFactoryBuilder.build(JaxRs2HttpClientRequest.class);
    }

    @Inject // internal dependency-injection constructor
    BraveClientRequestFilter(Brave brave, SpanNameProvider spanNameProvider) {
        this(builder(brave).spanNameProvider(spanNameProvider));
    }

    /**
     * @deprecated please use {@link #create(Brave)} or {@link #builder(Brave)}
     */
    @Deprecated
    public BraveClientRequestFilter(SpanNameProvider spanNameProvider, ClientRequestInterceptor interceptor) {
        this.interceptor = interceptor;
        this.adapterFactory = HttpClientRequestAdapter.factoryBuilder()
            .spanNameProvider(spanNameProvider)
            .build(JaxRs2HttpClientRequest.class);
    }


    @Override
    public void filter(ClientRequestContext clientRequestContext) throws IOException {
        ClientRequestAdapter adapter =
            adapterFactory.create(new JaxRs2HttpClientRequest(clientRequestContext));
        interceptor.handle(adapter);
    }
}
