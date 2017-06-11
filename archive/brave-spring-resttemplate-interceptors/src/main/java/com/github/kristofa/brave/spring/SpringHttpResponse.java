package com.github.kristofa.brave.spring;

import com.github.kristofa.brave.http.HttpResponse;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

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
