package com.github.kristofa.brave.cxf;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.cxf.message.Message;

import com.github.kristofa.brave.http.HttpClientRequest;

/**
 * @author Michał Podsiedzik
 */
public class ClientRequest extends MessageWrapper implements HttpClientRequest {
    public ClientRequest(final Message message) {
        super(message);
    }

    @Override
    public void addHeader(final String header, final String value) {
        super.addHeader(header, value);
    }

    @Override
    public URI getUri() {
        try {
            return new URI((String) message.getExchange().get(Message.ENDPOINT_ADDRESS));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getHttpMethod() {
        return super.getHttpMethod();
    }
}