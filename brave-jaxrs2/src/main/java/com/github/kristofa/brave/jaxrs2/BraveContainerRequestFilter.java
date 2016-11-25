package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.HttpServerRequest;
import com.github.kristofa.brave.http.HttpServerRequestAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;

import java.io.IOException;

import javax.inject.Inject;
import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.ext.Provider;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Intercepts incoming container requests and extracts any trace information from the request header
 * Also sends sr annotations.
 */
@Provider
@PreMatching
@Priority(0)
public class BraveContainerRequestFilter implements ContainerRequestFilter {

    /** Creates a tracing filter with defaults. Use {@link #builder(Brave)} to customize. */
    public static BraveContainerRequestFilter create(Brave brave) {
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

        public BraveContainerRequestFilter build() {
            return new BraveContainerRequestFilter(this);
        }
    }

    private final ServerRequestInterceptor requestInterceptor;
    private final SpanNameProvider spanNameProvider;

    BraveContainerRequestFilter(Builder b) { // intentionally hidden
        this.requestInterceptor = b.brave.serverRequestInterceptor();
        this.spanNameProvider = b.spanNameProvider;
    }

    @Inject // internal dependency-injection constructor
    BraveContainerRequestFilter(Brave brave, SpanNameProvider spanNameProvider) {
        this(builder(brave).spanNameProvider(spanNameProvider));
    }

    /**
     * @deprecated please use {@link #create(Brave)} or {@link #builder(Brave)}
     */
    @Deprecated
    public BraveContainerRequestFilter(ServerRequestInterceptor interceptor, SpanNameProvider spanNameProvider) {
        this.requestInterceptor = interceptor;
        this.spanNameProvider = spanNameProvider;
    }

    @Override
    public void filter(ContainerRequestContext containerRequestContext) throws IOException {
        HttpServerRequest request = new JaxRs2HttpServerRequest(containerRequestContext);
        requestInterceptor.handle(new HttpServerRequestAdapter(request, spanNameProvider));
    }

}
