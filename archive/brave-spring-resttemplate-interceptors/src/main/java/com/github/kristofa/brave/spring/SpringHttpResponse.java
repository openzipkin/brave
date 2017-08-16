package com.github.kristofa.brave.spring;

import com.github.kristofa.brave.http.HttpResponse;

class SpringHttpResponse implements HttpResponse {
    private final int status;

    SpringHttpResponse(final int status) {
        this.status = status;
    }

    @Override
    public int getHttpStatusCode() {
        return status;
    }
}
