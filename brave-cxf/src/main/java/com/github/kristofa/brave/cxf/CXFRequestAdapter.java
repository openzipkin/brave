package com.github.kristofa.brave.cxf;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.ClientRequestAdapter;
import com.google.common.base.Optional;
import org.apache.cxf.message.Message;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: fedor
 * Date: 17.01.2015
 */
class CXFRequestAdapter implements ClientRequestAdapter {

    private final Message message;
    private final String method;
    private final SpanAddress spanAddress;
    private final URI uri;
    Map<String, List> headers;

    public CXFRequestAdapter(final Message message) {
        this.message = message;
        this.method = (String)message.get(Message.HTTP_REQUEST_METHOD);
        this.uri = URI.create((String)message.get(Message.REQUEST_URI));
        this.spanAddress = new SpanAddress(uri);
        this.headers = (Map<String, List>) message.get(Message.PROTOCOL_HEADERS);
    }

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public String getMethod() {
        return method;
    }

    @Override
    public Optional<String> getSpanName() {
        Optional<String> spanName = Optional.absent();
        final String spanNameHeader = (String)message.getExchange().get(BraveHttpHeaders.SpanName.getName());
        if (spanNameHeader != null) {
            spanName = Optional.fromNullable(spanNameHeader);
        }
        else {
            spanName = Optional.fromNullable(spanAddress.getSpanName());
        }
        return spanName;
    }

    @Override
    public void addHeader(final String header, final String value) {

        headers.put(header, Collections.singletonList(value));
    }
}
