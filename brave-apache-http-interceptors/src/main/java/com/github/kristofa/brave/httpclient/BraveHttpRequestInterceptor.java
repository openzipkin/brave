package com.github.kristofa.brave.httpclient;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientRequestAdapter;
import com.github.kristofa.brave.TagExtractor;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Apache http client request interceptor.
 */
public class BraveHttpRequestInterceptor implements HttpRequestInterceptor {

    /** Creates a tracing interceptor with defaults. Use {@link #builder(Brave)} to customize. */
    public static BraveHttpRequestInterceptor create(Brave brave) {
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

        public BraveHttpRequestInterceptor build() {
            return new BraveHttpRequestInterceptor(this);
        }
    }

    private final ClientRequestInterceptor interceptor;
    private final ClientRequestAdapter.Factory<HttpClientRequestImpl> adapterFactory;

    BraveHttpRequestInterceptor(Builder b) { // intentionally hidden
        this.interceptor = b.brave.clientRequestInterceptor();
        this.adapterFactory = b.adapterFactoryBuilder.build(HttpClientRequestImpl.class);
    }

    /**
     * @deprecated please use {@link #create(Brave)} or {@link #builder(Brave)}
     */
    @Deprecated
    public BraveHttpRequestInterceptor(ClientRequestInterceptor interceptor, SpanNameProvider spanNameProvider) {
        this.interceptor = interceptor;
        this.adapterFactory = HttpClientRequestAdapter.factoryBuilder()
            .spanNameProvider(spanNameProvider)
            .build(HttpClientRequestImpl.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(final HttpRequest request, final HttpContext context) {
        ClientRequestAdapter adapter = adapterFactory.create(new HttpClientRequestImpl(request));
        interceptor.handle(adapter);
    }
}
