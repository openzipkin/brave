package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.http.HttpResponse;
import com.github.kristofa.brave.http.HttpServerResponseAdapter;
import java.io.IOException;
import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import zipkin.Constants;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Intercepts outgoing container responses and sends ss annotations.
 *
 * @deprecated Replaced by {@code TracingContainerFilter} from brave-instrumentation-jaxrs2
 */
@Deprecated
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
    // null on deprecated constructor
    private final ServerTracer serverTracer;

    BraveContainerResponseFilter(Builder b) { // intentionally hidden
        this.serverTracer = b.brave.serverTracer();
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
        this.serverTracer = null;
    }

    @Override
    public void filter(final ContainerRequestContext containerRequestContext, final ContainerResponseContext containerResponseContext) throws IOException {
        HttpResponse httpResponse = containerResponseContext::getStatus;
        Response.StatusType statusInfo = containerResponseContext.getStatusInfo();
        if (serverTracer != null && statusInfo.getFamily() == Response.Status.Family.SERVER_ERROR) {
            serverTracer.submitBinaryAnnotation(Constants.ERROR, statusInfo.getReasonPhrase());
        }
        responseInterceptor.handle(new HttpServerResponseAdapter(httpResponse));
    }
}
