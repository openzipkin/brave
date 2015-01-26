package com.github.kristofa.brave.cxf;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.ClientRequestAdapter;
import com.google.common.base.Optional;
import org.apache.cxf.message.Message;

import java.net.URI;

/**
 * User: fedor
 * Date: 17.01.2015
 */
class CXFRequestAdapter implements ClientRequestAdapter {

    private final Message message;

    public CXFRequestAdapter(final Message message) {
        this.message = message;
    }

    @Override
    public URI getUri() {
        return URI.create((String)message.get(Message.REQUEST_URI));
    }

    @Override
    public String getMethod() {
        return (String)message.get(Message.HTTP_REQUEST_METHOD);
    }

    @Override
    public Optional<String> getSpanName() {
        Optional<String> spanName = Optional.absent();
        final String spanNameHeader = (String)message.getExchange().get(BraveHttpHeaders.SpanName.getName());
        if (spanNameHeader != null) {
            spanName = Optional.fromNullable(spanNameHeader);
        }
        return spanName;
    }

    @Override
    public void addHeader(final String header, final String value) {
        message.getExchange().put(header, value);
    }
}
