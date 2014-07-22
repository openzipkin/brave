package com.github.kristofa.brave.httpclient;

import java.net.URI;

import org.apache.http.Header;
import org.apache.http.HttpRequest;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.ClientRequestAdapter;
import com.google.common.base.Optional;

class ApacheRequestAdapter implements ClientRequestAdapter {

    private final HttpRequest request;

    public ApacheRequestAdapter(final HttpRequest request) {
        this.request = request;
    }

    @Override
    public URI getUri() {
        return URI.create(request.getRequestLine().getUri());
    }

    @Override
    public String getMethod() {
        return request.getRequestLine().getMethod();
    }

    @Override
    public Optional<String> getSpanName() {
        Optional<String> spanName = Optional.absent();
        final Header spanNameHeader = request.getFirstHeader(BraveHttpHeaders.SpanName.getName());
        if (spanNameHeader != null) {
            spanName = Optional.fromNullable(spanNameHeader.getValue());
        }
        return spanName;
    }

    @Override
    public void addHeader(final String header, final String value) {
        request.addHeader(header, value);
    }
}
