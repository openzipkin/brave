package com.github.kristofa.brave.servlet;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import com.github.kristofa.brave.http.HttpServerRequest;


public class ServletHttpServerRequest implements HttpServerRequest {

    private static final Logger LOGGER = Logger.getLogger(ServletHttpServerRequest.class.getName());

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
        try
        {
            return URI.create(URLEncoder.encode(url.toString(), Charset.defaultCharset().name()));
        } catch (UnsupportedEncodingException uee){
            LOGGER.log(Level.WARNING, "Exception encoding string to URL", uee);
            return URI.create(url.toString());
        }
    }

    @Override
    public String getHttpMethod() {
        return request.getMethod();
    }
}
