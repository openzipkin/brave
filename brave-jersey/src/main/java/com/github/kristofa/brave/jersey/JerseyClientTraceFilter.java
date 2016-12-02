package com.github.kristofa.brave.jersey;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.HttpClientResponseAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.github.kristofa.brave.internal.Nullable;
import com.github.kristofa.brave.Propagation;
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

    public static final class Builder {
        final Brave brave;
        SpanNameProvider spanNameProvider = new DefaultSpanNameProvider();

        Builder(Brave brave) { // intentionally hidden
            this.brave = checkNotNull(brave, "brave");
        }

        public Builder spanNameProvider(SpanNameProvider spanNameProvider) {
            this.spanNameProvider = checkNotNull(spanNameProvider, "spanNameProvider");
            return this;
        }

        public JerseyClientTraceFilter build() {
            return new JerseyClientTraceFilter(this);
        }
    }

    @Nullable // nullable while deprecated constructor is in use
    private final Propagation.Injector<ClientRequest> injector;
    private final ClientRequestInterceptor requestInterceptor;
    private final ClientResponseInterceptor responseInterceptor;
    private final SpanNameProvider spanNameProvider;

    JerseyClientTraceFilter(Builder b) { // intentionally hidden
        this.injector = b.brave.propagation().injector(
            (carrier, key, value) -> carrier.getHeaders().putSingle(key, value));
        this.requestInterceptor = b.brave.clientRequestInterceptor();
        this.responseInterceptor = b.brave.clientResponseInterceptor();
        this.spanNameProvider = b.spanNameProvider;
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
        this.injector = null;
        this.spanNameProvider = spanNameProvider;
        this.requestInterceptor = requestInterceptor;
        this.responseInterceptor = responseInterceptor;
    }

    @Override
    public ClientResponse handle(final ClientRequest request) throws ClientHandlerException {
        HttpClientRequestAdapter requestAdapter =
            new HttpClientRequestAdapter(new JerseyHttpRequest(request), spanNameProvider);
        SpanId spanId = requestInterceptor.internalStartSpan(requestAdapter);
        if (injector != null) {
            injector.injectSpanId(spanId, request);
        } else {
            requestAdapter.addSpanIdToRequest(spanId);
        }

        final ClientResponse response = getNext().handle(request);
        responseInterceptor.handle(new HttpClientResponseAdapter(new JerseyHttpResponse(response)));
        return response;
    }
}
