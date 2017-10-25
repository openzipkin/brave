package com.github.kristofa.brave.servlet;

import java.net.URI;

import javax.servlet.http.HttpServletRequest;

import com.github.kristofa.brave.http.HttpServerRequest;
import com.google.common.net.PercentEscaper;


public class ServletHttpServerRequest implements HttpServerRequest
{
    /**
     * Safe chars list consumed from
     * <a href="https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/encodeURI">MDN</a>
     * as a excerpt from 2.2 of <a href="https://www.ietf.org/rfc/rfc2396.txt">RFC2396</a>
     */
    private static final PercentEscaper ESCAPER = new PercentEscaper(";,/?:@&=+$-_.!~*'()#", false);

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
        return URI.create(ESCAPER.escape(url.toString()));
    }

    @Override
    public String getHttpMethod()
    {
        return request.getMethod();
    }
}
