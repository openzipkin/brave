package com.github.kristofa.brave.servlet;

import com.github.kristofa.brave.http.HttpServerRequest;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;

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
        StringBuffer url = request.getRequestURL();
        if (request.getQueryString() != null && !request.getQueryString().isEmpty()) {
            url.append('?').append(request.getQueryString());
        }
        return URI.create(url.toString());
    }

    @Override
    public String getHttpMethod() {
        return request.getMethod();
    }
}
