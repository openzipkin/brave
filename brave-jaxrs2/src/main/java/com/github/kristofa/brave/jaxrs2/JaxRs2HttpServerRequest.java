package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.http.HttpServerRequest;

import javax.ws.rs.container.ContainerRequestContext;
import java.net.URI;

public class JaxRs2HttpServerRequest implements HttpServerRequest {

    private final ContainerRequestContext containerRequestContext;

    public JaxRs2HttpServerRequest(ContainerRequestContext containerRequestContext) {
        this.containerRequestContext = containerRequestContext;
    }

    @Override
    public String getHttpHeaderValue(String headerName) {
        return containerRequestContext.getHeaderString(headerName);
    }

    @Override
    public URI getUri() {
        return containerRequestContext.getUriInfo().getRequestUri();
    }

    @Override
    public String getHttpMethod() {
        return containerRequestContext.getMethod();
    }
}
