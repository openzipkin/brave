package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.*;
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

/**
 * Intercepts incoming container requests and extracts any trace information from the request header
 * Also sends sr annotations.
 */
@Provider
@PreMatching
@Priority(0)
public class BraveContainerRequestFilter implements ContainerRequestFilter {

    private final ServerRequestInterceptor requestInterceptor;
    private final SpanNameProvider spanNameProvider;

    @Inject
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
