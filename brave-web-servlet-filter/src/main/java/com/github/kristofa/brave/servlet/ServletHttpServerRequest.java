package com.github.kristofa.brave.servlet;

import com.github.kristofa.brave.http.HttpServerRequest;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;

public class ServletHttpServerRequest implements HttpServerRequest {

    private final HttpServletRequest request;

    public ServletHttpServerRequest(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public String getHttpHeaderValue(String headerName) {
        return request.getHeader(headerName);
    }

    @Override
    public URI getUri() {
        return URI.create(request.getRequestURI());
    }

    @Override
    public String getHttpMethod() {
        return request.getMethod();
    }
}
