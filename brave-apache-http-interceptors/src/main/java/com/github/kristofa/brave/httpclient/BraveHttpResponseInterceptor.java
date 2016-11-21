package com.github.kristofa.brave.httpclient;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientResponseAdapter;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.TagExtractor;
import com.github.kristofa.brave.http.HttpClientResponseAdapter;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Apache http client response interceptor.
 */
public class BraveHttpResponseInterceptor implements HttpResponseInterceptor {

    /** Creates a tracing interceptor with defaults. Use {@link #builder(Brave)} to customize. */
    public static BraveHttpResponseInterceptor create(Brave brave) {
        return new Builder(brave).build();
    }

    public static Builder builder(Brave brave) {
        return new Builder(brave);
    }

    public static final class Builder implements TagExtractor.Config<Builder> {
        final Brave brave;
        final HttpClientResponseAdapter.FactoryBuilder adapterFactoryBuilder
            = HttpClientResponseAdapter.factoryBuilder();

        Builder(Brave brave) { // intentionally hidden
            this.brave = checkNotNull(brave, "brave");
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

        public BraveHttpResponseInterceptor build() {
            return new BraveHttpResponseInterceptor(this);
        }
    }

    private final ClientResponseInterceptor interceptor;
    private final ClientResponseAdapter.Factory<HttpClientResponseImpl> adapterFactory;

    BraveHttpResponseInterceptor(Builder b) { // intentionally hidden
        this.interceptor = b.brave.clientResponseInterceptor();
        this.adapterFactory = b.adapterFactoryBuilder.build(HttpClientResponseImpl.class);
    }

    /**
     * @deprecated please use {@link #create(Brave)} or {@link #builder(Brave)}
     */
    @Deprecated
    public BraveHttpResponseInterceptor(final ClientResponseInterceptor interceptor) {
        this.interceptor = interceptor;
        this.adapterFactory = HttpClientResponseAdapter.factoryBuilder()
            .build(HttpClientResponseImpl.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(final HttpResponse response, final HttpContext context) throws HttpException, IOException {
        ClientResponseAdapter adapter = adapterFactory.create(new HttpClientResponseImpl(response));
        interceptor.handle(adapter);
    }

}
