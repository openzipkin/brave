package com.github.kristofa.brave.httpclient;

import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.http.HttpClientResponseAdapter;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;

/**
 * Apache http client response interceptor.
 */
public class BraveHttpResponseInterceptor implements HttpResponseInterceptor {

    private final ClientResponseInterceptor responseInterceptor;

    public BraveHttpResponseInterceptor(final ClientResponseInterceptor responseInterceptor) {
        this.responseInterceptor = responseInterceptor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(final HttpResponse response, final HttpContext context) throws HttpException, IOException {
        final HttpClientResponseImpl httpClientResponse = new HttpClientResponseImpl(response);
        responseInterceptor.handle(new HttpClientResponseAdapter(httpClientResponse));
    }

}
