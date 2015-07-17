package com.github.kristofa.brave.resteasy;


import com.github.kristofa.brave.http.HttpClientRequest;
import org.jboss.resteasy.client.ClientRequest;

import java.net.URI;

public class RestEasyHttpClientRequest implements HttpClientRequest {

    private final ClientRequest request;

    RestEasyHttpClientRequest(ClientRequest request) {
        this.request = request;
    }


    @Override
    public void addHeader(String header, String value) {
        request.header(header, value);
    }

    @Override
    public URI getUri() {
        try {
            return URI.create(request.getUri());
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getHttpMethod() {
        return request.getHttpMethod();
    }
}
