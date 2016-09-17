package com.github.kristofa.brave.cxf;

import org.apache.cxf.message.Message;

import com.github.kristofa.brave.http.HttpResponse;

/**
 * @author Michał Podsiedzik
 */
public class ClientResponse extends MessageWrapper implements HttpResponse {
    public ClientResponse(final Message message) {
        super(message);
    }

    @Override
    public int getHttpStatusCode() {
        return super.getHttpResponseCode();
    }
}