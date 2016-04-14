package com.github.kristofa.brave.httpclient;

import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;

import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;

/**
 * Apache http client request interceptor.
 */
public class BraveHttpRequestInterceptor implements HttpRequestInterceptor {

    private final SpanNameProvider spanNameProvider;
    private final ClientRequestInterceptor requestInterceptor;

    /**
     * Creates a new instance.
     *
     * @param requestInterceptor
     * @param spanNameProvider Provides span name for request.
     */
    public BraveHttpRequestInterceptor(ClientRequestInterceptor requestInterceptor, SpanNameProvider spanNameProvider) {
        this.requestInterceptor = requestInterceptor;
        this.spanNameProvider = spanNameProvider;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(final HttpRequest request, final HttpContext context) {
        HttpClientRequestAdapter adapter = new HttpClientRequestAdapter(new HttpClientRequestImpl(request), spanNameProvider);
        requestInterceptor.handle(adapter);
    }
}
