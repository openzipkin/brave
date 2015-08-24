package com.github.kristofa.brave.httpclient;

import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.ServiceNameProvider;
import com.github.kristofa.brave.http.SpanNameProvider;

import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;

/**
 * Apache http client request interceptor.
 */
public class BraveHttpRequestInterceptor implements HttpRequestInterceptor {

    private final ServiceNameProvider serviceNameProvider;
    private final SpanNameProvider spanNameProvider;
    private final ClientRequestInterceptor requestInterceptor;

    /**
     * Creates a new instance.
     *
     * @param requestInterceptor
     * @param serviceNameProvider Provides service name for request.
     * @param spanNameProvider Provides span name for request.
     */
    public BraveHttpRequestInterceptor(ClientRequestInterceptor requestInterceptor, ServiceNameProvider serviceNameProvider, SpanNameProvider spanNameProvider) {
        this.requestInterceptor = requestInterceptor;
        this.serviceNameProvider = serviceNameProvider;
        this.spanNameProvider = spanNameProvider;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(final HttpRequest request, final HttpContext context) {
        HttpClientRequestAdapter adapter = new HttpClientRequestAdapter(new HttpClientRequestImpl(request), serviceNameProvider, spanNameProvider);
        requestInterceptor.handle(adapter);
    }
}
