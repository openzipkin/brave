package com.github.kristofa.brave.cxf;

import com.github.kristofa.brave.http.HttpClientRequest;
import org.apache.cxf.message.Message;

import java.net.URI;
import java.net.URISyntaxException;

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