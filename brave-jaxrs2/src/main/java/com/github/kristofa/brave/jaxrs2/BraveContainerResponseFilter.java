package com.github.kristofa.brave.jaxrs2;

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

/**
 * Intercepts outgoing container responses and sends ss annotations.
 */
@Provider
@Priority(0)
public class BraveContainerResponseFilter implements ContainerResponseFilter {

    private final ServerResponseInterceptor responseInterceptor;

    @Inject
    public BraveContainerResponseFilter(ServerResponseInterceptor responseInterceptor) {
        this.responseInterceptor = responseInterceptor;
    }

    @Override
    public void filter(final ContainerRequestContext containerRequestContext, final ContainerResponseContext containerResponseContext) throws IOException {

        HttpResponse httpResponse = new HttpResponse() {

            @Override
            public int getHttpStatusCode() {
                return containerResponseContext.getStatus();
            }
        };

        responseInterceptor.handle(new HttpServerResponseAdapter(httpResponse));
    }
}
