package com.github.kristofa.brave.jersey2;

import com.github.kristofa.brave.ServerTracer;

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
public class BraveContainerResponseFilter implements ContainerResponseFilter {

    private final ServerTracer serverTracer;

    @Inject
    public BraveContainerResponseFilter(ServerTracer serverTracer) {
        this.serverTracer = serverTracer;
    }

    @Override
    public void filter(ContainerRequestContext containerRequestContext, ContainerResponseContext containerResponseContext) throws IOException {
        try {
            serverTracer.setServerSend();
        } finally {
            serverTracer.clearCurrentSpan();
        }
    }
}
