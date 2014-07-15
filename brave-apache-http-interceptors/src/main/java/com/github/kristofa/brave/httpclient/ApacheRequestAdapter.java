package com.github.kristofa.brave.httpclient;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.ClientRequestAdapter;
import com.google.common.base.Optional;
import org.apache.http.Header;
import org.apache.http.HttpRequest;

import java.net.URI;

public class ApacheRequestAdapter implements ClientRequestAdapter {
    private final HttpRequest request;

    public ApacheRequestAdapter(HttpRequest request) {
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
        Header spanNameHeader = request.getFirstHeader(BraveHttpHeaders.SpanName.getName());
        if(spanNameHeader != null) {
            spanName = Optional.fromNullable(spanNameHeader.getValue());
        }
        return spanName;
    }

    @Override
    public void addHeader(String header, String value) {
        request.addHeader(header, value);
    }
}
