package com.github.kristofa.brave.cxf;

import com.github.kristofa.brave.http.HttpResponse;
import org.apache.cxf.message.Message;

/**
 * @author Michał Podsiedzik
 */
public class ServerResponse extends MessageWrapper implements HttpResponse {
    public ServerResponse(final Message message) {
        super(message);
    }

    @Override
    public int getHttpStatusCode() {
        return super.getHttpResponseCode();
    }
}