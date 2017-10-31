package com.github.kristofa.brave.servlet;

import java.net.URI;

import javax.servlet.http.HttpServletRequest;

import com.github.kristofa.brave.http.HttpServerRequest;
import com.github.kristofa.brave.util.UriEscaperUtil;


public class ServletHttpServerRequest implements HttpServerRequest
{

    private final HttpServletRequest request;

    public ServletHttpServerRequest(HttpServletRequest request)
    {
        this.request = request;
    }

    @Override
    public String getHttpHeaderValue(String headerName)
    {
        return request.getHeader(headerName);
    }

    @Override
    public URI getUri()
    {
        StringBuffer url = request.getRequestURL();
        if (request.getQueryString() != null && !request.getQueryString().isEmpty())
        {
            url.append("?").append(request.getQueryString());
        }
        try
        {
            return URI.create(url.toString());
        }
        catch (IllegalArgumentException iae)
        {
            return URI.create(UriEscaperUtil.escape(url.toString()));
        }
    }

    @Override
    public String getHttpMethod()
    {
        return request.getMethod();
    }
}
