package com.github.kristofa.brave.cxf;

import com.github.kristofa.brave.ClientResponseAdapter;
import org.apache.cxf.message.Message;

/**
 * User: fedor
 * Date: 17.01.2015
 */
class CXFClientResponseAdapter implements ClientResponseAdapter {

    private final Message message;

    public CXFClientResponseAdapter(final Message message) {
        this.message = message;
    }

    @Override
    public int getStatusCode() {
        return Integer.getInteger(message.get(Message.RESPONSE_CODE).toString(), 0);
    }
}
