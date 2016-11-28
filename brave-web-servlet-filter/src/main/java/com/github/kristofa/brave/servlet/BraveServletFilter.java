package com.github.kristofa.brave.servlet;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerRequestAdapter;
import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.ServerResponseAdapter;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.TagExtractor;
import com.github.kristofa.brave.http.HttpResponse;
import com.github.kristofa.brave.http.HttpServerRequestAdapter;
import com.github.kristofa.brave.http.HttpServerResponseAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;

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

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Servlet filter that will extract trace headers from the request and send
 * sr (server received) and ss (server sent) annotations.
 */
public class BraveServletFilter implements Filter {

    /** Creates a tracing filter with defaults. Use {@link #builder(Brave)} to customize. */
    public static BraveServletFilter create(Brave brave) {
        return new Builder(brave).build();
    }

    public static Builder builder(Brave brave) {
        return new Builder(brave);
    }

    public static final class Builder implements TagExtractor.Config<Builder> {
        final Brave brave;
        final HttpServerRequestAdapter.FactoryBuilder requestFactoryBuilder
            = HttpServerRequestAdapter.factoryBuilder();
        final HttpServerResponseAdapter.FactoryBuilder responseFactoryBuilder
            = HttpServerResponseAdapter.factoryBuilder();

        Builder(Brave brave) { // intentionally hidden
            this.brave = checkNotNull(brave, "brave");
        }


        public Builder spanNameProvider(SpanNameProvider spanNameProvider) {
            requestFactoryBuilder.spanNameProvider(spanNameProvider);
            return this;
        }

        @Override public Builder addKey(String key) {
            requestFactoryBuilder.addKey(key);
            responseFactoryBuilder.addKey(key);
            return this;
        }

        @Override
        public Builder addValueParserFactory(TagExtractor.ValueParserFactory factory) {
            requestFactoryBuilder.addValueParserFactory(factory);
            responseFactoryBuilder.addValueParserFactory(factory);
            return this;
        }

        public BraveServletFilter build() {
            return new BraveServletFilter(this);
        }
    }

    private final ServerRequestInterceptor requestInterceptor;
    private final ServerResponseInterceptor responseInterceptor;
    private final ServerRequestAdapter.Factory<ServletHttpServerRequest> requestAdapterFactory;
    private final ServerResponseAdapter.Factory<HttpResponse> responseAdapterFactory;

    private FilterConfig filterConfig;

    protected BraveServletFilter(Builder b) { // intentionally hidden
        this.requestInterceptor = b.brave.serverRequestInterceptor();
        this.responseInterceptor = b.brave.serverResponseInterceptor();
        this.requestAdapterFactory = b.requestFactoryBuilder.build(ServletHttpServerRequest.class);
        this.responseAdapterFactory = b.responseFactoryBuilder.build(HttpResponse.class);
    }

    /**
     * @deprecated please use {@link #create(Brave)} or {@link #builder(Brave)}
     */
    @Deprecated
    public BraveServletFilter(ServerRequestInterceptor requestInterceptor, ServerResponseInterceptor responseInterceptor, SpanNameProvider spanNameProvider) {
        this.requestInterceptor = requestInterceptor;
        this.responseInterceptor = responseInterceptor;
        this.requestAdapterFactory = HttpServerRequestAdapter.factoryBuilder()
            .spanNameProvider(spanNameProvider).build(ServletHttpServerRequest.class);
        this.responseAdapterFactory = HttpServerResponseAdapter.factoryBuilder()
            .build(HttpResponse.class);
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

            final StatusExposingServletResponse statusExposingServletResponse = new StatusExposingServletResponse((HttpServletResponse) response);
            try {
                ServerRequestAdapter adapter = requestAdapterFactory.create(new ServletHttpServerRequest(
                    (HttpServletRequest) request));
                requestInterceptor.handle(adapter);

                filterChain.doFilter(request, statusExposingServletResponse);
            } finally {
                ServerResponseAdapter adapter = responseAdapterFactory.create(new HttpResponse() {
                        @Override
                        public int getHttpStatusCode() {
                            return statusExposingServletResponse.getStatus();
                        }
                    });
                responseInterceptor.handle(adapter);
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


    private static class StatusExposingServletResponse extends HttpServletResponseWrapper {
        // The Servlet spec says: calling setStatus is optional, if no status is set, the default is OK.
        private int httpStatus = HttpServletResponse.SC_OK;

        public StatusExposingServletResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void sendError(int sc) throws IOException {
            httpStatus = sc;
            super.sendError(sc);
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            httpStatus = sc;
            super.sendError(sc, msg);
        }

        @Override
        public void setStatus(int sc) {
            httpStatus = sc;
            super.setStatus(sc);
        }

        public int getStatus() {
            return httpStatus;
        }
    }
}
