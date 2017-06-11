package com.github.kristofa.brave.servlet;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.ServerResponseAdapter;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.HttpServerRequestAdapter;
import com.github.kristofa.brave.http.HttpServerResponseAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;

import com.github.kristofa.brave.internal.Nullable;
import com.github.kristofa.brave.servlet.internal.MaybeAddClientAddressFromRequest;
import java.util.Collection;
import java.util.Collections;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import zipkin.Constants;
import zipkin.TraceKeys;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Servlet filter that will extract trace headers from the request and send
 * sr (server received) and ss (server sent) annotations.
 *
 * @deprecated Replaced by {@code TracingFilter} from brave-instrumentation-servlet
 */
public class BraveServletFilter implements Filter {

    /** Creates a tracing filter with defaults. Use {@link #builder(Brave)} to customize. */
    public static BraveServletFilter create(Brave brave) {
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

        public BraveServletFilter build() {
            return new BraveServletFilter(this);
        }
    }

    private final ServerRequestInterceptor requestInterceptor;
    private final ServerResponseInterceptor responseInterceptor;
    private final SpanNameProvider spanNameProvider;
    @Nullable // while deprecated constructor is in use
    private final ServerTracer serverTracer;
    private final MaybeAddClientAddressFromRequest maybeAddClientAddressFromRequest;

    private FilterConfig filterConfig;

    protected BraveServletFilter(Builder b) { // intentionally hidden
        this.requestInterceptor = b.brave.serverRequestInterceptor();
        this.responseInterceptor = b.brave.serverResponseInterceptor();
        this.spanNameProvider = b.spanNameProvider;
        this.serverTracer = b.brave.serverTracer();
        this.maybeAddClientAddressFromRequest = MaybeAddClientAddressFromRequest.create(b.brave);
    }

    /**
     * @deprecated please use {@link #create(Brave)} or {@link #builder(Brave)}
     */
    @Deprecated
    public BraveServletFilter(ServerRequestInterceptor requestInterceptor, ServerResponseInterceptor responseInterceptor, SpanNameProvider spanNameProvider) {
        this.requestInterceptor = requestInterceptor;
        this.responseInterceptor = responseInterceptor;
        this.spanNameProvider = spanNameProvider;
        this.serverTracer = null;
        this.maybeAddClientAddressFromRequest = null;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.filterConfig = filterConfig;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {

        String alreadyFilteredAttributeName = getAlreadyFilteredAttributeName();
        boolean hasAlreadyFilteredAttribute = request.getAttribute(alreadyFilteredAttributeName) != null;

        if (hasAlreadyFilteredAttribute) {
            // Proceed without invoking this filter...
            filterChain.doFilter(request, response);
        } else {
            request.setAttribute(alreadyFilteredAttributeName, Boolean.TRUE);

            HttpServletRequest httpRequest = (HttpServletRequest) request;
            final Servlet25ServerResponseAdapter
                servlet25ServerResponseAdapter = new Servlet25ServerResponseAdapter((HttpServletResponse) response);
            requestInterceptor.handle(new HttpServerRequestAdapter(new ServletHttpServerRequest(httpRequest), spanNameProvider));

            if (maybeAddClientAddressFromRequest != null) {
                maybeAddClientAddressFromRequest.accept(httpRequest);
            }

            try {
                filterChain.doFilter(request, servlet25ServerResponseAdapter);
            } catch (IOException | ServletException| RuntimeException | Error e) {
                if (serverTracer != null) {
                    // TODO: revisit https://github.com/openzipkin/openzipkin.github.io/issues/52
                    String message = e.getMessage();
                    if (message == null) message = e.getClass().getSimpleName();
                    serverTracer.submitBinaryAnnotation(Constants.ERROR, message);
                }
                throw e;
            } finally {
                responseInterceptor.handle(servlet25ServerResponseAdapter);
            }
        }
    }

    @Override
    public void destroy() {

    }

    private String getAlreadyFilteredAttributeName() {
        String name = getFilterName();
        if (name == null) {
            name = getClass().getName();
        }
        return name + ".FILTERED";
    }

    private final String getFilterName() {
        return (this.filterConfig != null ? this.filterConfig.getFilterName() : null);
    }

    /** When deployed in Servlet 2.5 environment {@link #getStatus} is not available. */
    static final class Servlet25ServerResponseAdapter extends HttpServletResponseWrapper implements
        ServerResponseAdapter {
        // The Servlet spec says: calling setStatus is optional, if no status is set, the default is OK.
        int httpStatus = HttpServletResponse.SC_OK;

        Servlet25ServerResponseAdapter(HttpServletResponse response) {
            super(response);
        }

        @Override public void setStatus(int sc, String sm) {
            httpStatus = sc;
            super.setStatus(sc, sm);
        }

        @Override public void sendError(int sc) throws IOException {
            httpStatus = sc;
            super.sendError(sc);
        }

        @Override public void sendError(int sc, String msg) throws IOException {
            httpStatus = sc;
            super.sendError(sc, msg);
        }

        @Override public void setStatus(int sc) {
            httpStatus = sc;
            super.setStatus(sc);
        }

        /** Alternative to {@link #getStatus}, but for Servlet 2.5+ */
        @Override public Collection<KeyValueAnnotation> responseAnnotations() {
            return Collections.singleton(
                KeyValueAnnotation.create(TraceKeys.HTTP_STATUS_CODE, String.valueOf(httpStatus))
            );
        }
    }
}
