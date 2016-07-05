package com.github.kristofa.brave.cxf;

import com.github.kristofa.brave.http.HttpServerRequest;
import org.apache.cxf.message.Message;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author Michał Podsiedzik
 */
public class ServerRequest extends MessageWrapper implements HttpServerRequest {
    public ServerRequest(final Message message) {
        super(message);
    }

    @Override
    public String getHttpHeaderValue(final String headerName) {
        return super.getHeader(headerName);
    }

    @Override
    public URI getUri() {
        try {
            return new URI((String) message.get(Message.REQUEST_URL));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getHttpMethod() {
        return super.getHttpMethod();
    }
}