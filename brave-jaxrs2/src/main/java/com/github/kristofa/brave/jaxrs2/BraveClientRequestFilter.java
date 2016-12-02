package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.HttpClientRequest;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.github.kristofa.brave.internal.Nullable;
import com.github.kristofa.brave.Propagation;
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

        public BraveClientRequestFilter build() {
            return new BraveClientRequestFilter(this);
        }
    }

    @Nullable // nullable while deprecated constructor is in use
    private final Propagation.Injector<ClientRequestContext> injector;
    private final ClientRequestInterceptor interceptor;
    private final SpanNameProvider spanNameProvider;

    BraveClientRequestFilter(Builder b) { // intentionally hidden
        this.injector = b.brave.propagation().injector(
            (carrier, key, value) -> carrier.getHeaders().putSingle(key, value));
        this.interceptor = b.brave.clientRequestInterceptor();
        this.spanNameProvider = b.spanNameProvider;
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
        this.injector = null;
        this.interceptor = interceptor;
        this.spanNameProvider = spanNameProvider;
    }


    @Override
    public void filter(ClientRequestContext context) throws IOException {
        HttpClientRequest req = new JaxRs2HttpClientRequest(context);
        HttpClientRequestAdapter adapter = new HttpClientRequestAdapter(req, spanNameProvider);
        SpanId spanId = interceptor.internalStartSpan(adapter);
        if (injector != null) {
            injector.injectSpanId(spanId, context);
        } else {
            adapter.addSpanIdToRequest(spanId);
        }
    }
}
