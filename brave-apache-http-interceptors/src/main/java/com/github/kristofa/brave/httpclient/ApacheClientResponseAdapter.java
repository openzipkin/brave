package com.github.kristofa.brave.httpclient;

import com.github.kristofa.brave.ClientResponseAdapter;
import org.apache.http.HttpResponse;

public class ApacheClientResponseAdapter implements ClientResponseAdapter {
    private final HttpResponse response;

    public ApacheClientResponseAdapter(HttpResponse response) {
        this.response = response;
    }

    @Override
    public int getStatusCode() {
        return response.getStatusLine().getStatusCode();
    }
}
