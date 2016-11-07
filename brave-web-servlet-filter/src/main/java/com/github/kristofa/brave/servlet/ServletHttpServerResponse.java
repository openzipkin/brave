package com.github.kristofa.brave.servlet;

import javax.servlet.http.HttpServletResponse;

import com.github.kristofa.brave.http.HttpResponse;

public class ServletHttpServerResponse implements HttpResponse {

    private final HttpServletResponse response;

    public ServletHttpServerResponse(HttpServletResponse response) {
        this.response = response;
    }

    @Override
    public int getHttpStatusCode() {
        return response.getStatus();
    }
  
}
