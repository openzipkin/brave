package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.http.HttpResponse;
import com.github.kristofa.brave.http.HttpServerResponseAdapter;
import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Intercepts outgoing container responses and sends ss annotations.
 */
@Provider
@Priority(0)
public class BraveContainerResponseFilter implements ContainerResponseFilter {

    /** Creates a tracing filter with defaults. Use {@link #builder(Brave)} to customize. */
    public static BraveContainerResponseFilter create(Brave brave) {
        return new Builder(brave).build();
    }

    public static Builder builder(Brave brave) {
        return new Builder(brave);
    }

    public static final class Builder {
        final Brave brave;

        Builder(Brave brave) { // intentionally hidden
            this.brave = checkNotNull(brave, "brave");
        }

        public BraveContainerResponseFilter build() {
            return new BraveContainerResponseFilter(this);
        }
    }

    private final ServerResponseInterceptor responseInterceptor;

    BraveContainerResponseFilter(Builder b) { // intentionally hidden
        this.responseInterceptor = b.brave.serverResponseInterceptor();
    }

    @Inject // internal dependency-injection constructor
    BraveContainerResponseFilter(Brave brave) {
        this(builder(brave));
    }

    /**
     * @deprecated please use {@link #create(Brave)} or {@link #builder(Brave)}
     */
    @Deprecated
    public BraveContainerResponseFilter(ServerResponseInterceptor responseInterceptor) {
        this.responseInterceptor = responseInterceptor;
    }

    @Override
    public void filter(final ContainerRequestContext containerRequestContext, final ContainerResponseContext containerResponseContext) throws IOException {
        HttpResponse httpResponse = containerResponseContext::getStatus;
        responseInterceptor.handle(new HttpServerResponseAdapter(httpResponse));
    }
}
