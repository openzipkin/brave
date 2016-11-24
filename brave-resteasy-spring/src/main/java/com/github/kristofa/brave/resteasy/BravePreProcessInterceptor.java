package com.github.kristofa.brave.resteasy;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.HttpServerRequest;
import com.github.kristofa.brave.http.HttpServerRequestAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ResourceMethod;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.Failure;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.interception.PreProcessInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Rest Easy {@link PreProcessInterceptor} that will:
 * <ol>
 * <li>Get trace data (trace id, span id, parent span id) from http headers and initialize state for request + submit 'server
 * received' for request.</li>
 * <li>If no trace information is submitted we will start a new span. In that case it means client does not support tracing
 * and should be adapted.</li>
 * </ol>
 *
 * @author kristof
 */
@Component
@Provider
@ServerInterceptor
public class BravePreProcessInterceptor implements PreProcessInterceptor {

    /** Creates a tracing interceptor with defaults. Use {@link #builder(Brave)} to customize. */
    public static BravePreProcessInterceptor create(Brave brave) {
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

        public BravePreProcessInterceptor build() {
            return new BravePreProcessInterceptor(this);
        }
    }

    private final ServerRequestInterceptor requestInterceptor;
    private final SpanNameProvider spanNameProvider;

    @Context
    HttpServletRequest servletRequest;

    @Autowired // internal
    BravePreProcessInterceptor(SpanNameProvider spanNameProvider, Brave brave) {
        this(builder(brave).spanNameProvider(spanNameProvider));
    }

    BravePreProcessInterceptor(Builder b) { // intentionally hidden
        this.requestInterceptor = b.brave.serverRequestInterceptor();
        this.spanNameProvider = b.spanNameProvider;
    }

    /**
     * Creates a new instance.
     *
     * @param requestInterceptor Request interceptor.
     * @param spanNameProvider Span name provider.
     * @deprecated please use {@link #create(Brave)} or {@link #builder(Brave)}
     */
    @Deprecated
    public BravePreProcessInterceptor(ServerRequestInterceptor requestInterceptor,
                                      SpanNameProvider spanNameProvider
    ) {
        this.requestInterceptor = requestInterceptor;
        this.spanNameProvider = spanNameProvider;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServerResponse preProcess(final HttpRequest request, final ResourceMethod method) throws Failure,
        WebApplicationException {

        HttpServerRequest req = new RestEasyHttpServerRequest(request);
        HttpServerRequestAdapter reqAdapter = new HttpServerRequestAdapter(req, spanNameProvider);
        requestInterceptor.handle(reqAdapter);
        return null;
    }

}
