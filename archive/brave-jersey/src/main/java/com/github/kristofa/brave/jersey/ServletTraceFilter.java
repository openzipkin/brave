package com.github.kristofa.brave.jersey;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.github.kristofa.brave.servlet.BraveServletFilter;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Servlet filter that will extract trace headers from the request and send
 * sr (server received) and ss (server sent) annotations.
 */
@Singleton
public class ServletTraceFilter extends BraveServletFilter {

    @Inject // internal dependency-injection constructor
    ServletTraceFilter(SpanNameProvider spanNameProvider, Brave brave) {
       super(builder(brave).spanNameProvider(spanNameProvider));
    }

    /**
     * @deprecated please use {@link #create(Brave)} or {@link #builder(Brave)}
     */
    @Deprecated
    public ServletTraceFilter(
            ServerRequestInterceptor requestInterceptor,
            ServerResponseInterceptor responseInterceptor,
            SpanNameProvider spanNameProvider) {
       super(requestInterceptor, responseInterceptor, spanNameProvider);
    }
}
