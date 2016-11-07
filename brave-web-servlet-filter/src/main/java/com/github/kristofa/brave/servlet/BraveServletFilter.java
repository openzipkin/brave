package com.github.kristofa.brave.servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import com.github.kristofa.brave.ServerAdapterInterceptor;
import com.github.kristofa.brave.ServerRequestAdapter;
import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.ServerResponseAdapter;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.http.HttpResponse;
import com.github.kristofa.brave.http.HttpServerRequest;
import com.github.kristofa.brave.http.HttpServerRequestAdapter;
import com.github.kristofa.brave.http.HttpServerResponseAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;

/**
 * Servlet filter that will extract trace headers from the request and send
 * sr (server received) and ss (server sent) annotations.
 */
public class BraveServletFilter implements Filter {

    private final ServerRequestInterceptor requestInterceptor;
    private final ServerResponseInterceptor responseInterceptor;
    private final SpanNameProvider spanNameProvider;

    private FilterConfig filterConfig;
    
    private List<ServerAdapterInterceptor> adapterInterceptors = new ArrayList<ServerAdapterInterceptor>();

    public BraveServletFilter(ServerRequestInterceptor requestInterceptor, ServerResponseInterceptor responseInterceptor, SpanNameProvider spanNameProvider) {
        this.requestInterceptor = requestInterceptor;
        this.responseInterceptor = responseInterceptor;
        this.spanNameProvider = spanNameProvider;
    }
    
    public void addAdapterInterceptor(ServerAdapterInterceptor adapterInterceptor){
        adapterInterceptors.add(adapterInterceptor);
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

            final StatusExposingServletResponse statusExposingServletResponse = new StatusExposingServletResponse(
                    (HttpServletResponse) response);
            requestInterceptor.handle(interceptRequestAdapter(newRequestAdapter(newRequest((HttpServletRequest) request), spanNameProvider)));

            try {
                filterChain.doFilter(request, statusExposingServletResponse);
            }finally {
                HttpResponse httpResponse = newResposne(statusExposingServletResponse);
                responseInterceptor.handle(interceptResponseAdapter(newResponseAdapter(httpResponse)));
            }
        }
    }
    
    protected ServerRequestAdapter newRequestAdapter(HttpServerRequest servletHttpServerRequest,
            SpanNameProvider spanNameProvider) {
        return new HttpServerRequestAdapter(servletHttpServerRequest, spanNameProvider);
    }
  
    protected ServerResponseAdapter newResponseAdapter(HttpResponse httpResponse) {
        return new HttpServerResponseAdapter(httpResponse);
    }
    
    protected HttpServerRequest newRequest(HttpServletRequest request) {
        return new ServletHttpServerRequest(request);
    }
    
    protected HttpResponse newResposne(HttpServletResponse response) {
        return new ServletHttpServerResponse(response);
    }
    
    protected ServerRequestAdapter interceptRequestAdapter(ServerRequestAdapter adapter) {
        ServerRequestAdapter interceptedAdapter = adapter;
        for(ServerAdapterInterceptor adapterInterceptor : adapterInterceptors){
            interceptedAdapter = adapterInterceptor.interceptRequestAdapter(interceptedAdapter);
        }
        return interceptedAdapter;
    }
    
    protected ServerResponseAdapter interceptResponseAdapter(ServerResponseAdapter adapter) {
        ServerResponseAdapter interceptedAdapter = adapter;
        for(ServerAdapterInterceptor adapterInterceptor : adapterInterceptors){
            interceptedAdapter = adapterInterceptor.interceptResponseAdapter(interceptedAdapter);
        }
        return interceptedAdapter;
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
