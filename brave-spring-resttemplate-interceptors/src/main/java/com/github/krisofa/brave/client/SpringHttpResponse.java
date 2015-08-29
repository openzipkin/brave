package com.github.krisofa.brave.client;

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
