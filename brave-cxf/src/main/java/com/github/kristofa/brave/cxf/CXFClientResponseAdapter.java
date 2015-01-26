package com.github.kristofa.brave.cxf;

import com.github.kristofa.brave.ClientResponseAdapter;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.http.AbstractHTTPDestination;

import javax.servlet.http.HttpServletResponse;

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
        //HttpServletResponse response = (HttpServletResponse)message.get(AbstractHTTPDestination.HTTP_RESPONSE);

        Integer code = (Integer)message.getExchange().get(Message.RESPONSE_CODE);
        if (code != null)
            return code;
        return 0;
    }
}
