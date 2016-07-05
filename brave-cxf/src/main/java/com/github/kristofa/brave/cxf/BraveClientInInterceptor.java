package com.github.kristofa.brave.cxf;

import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.http.HttpClientResponseAdapter;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.StaxInInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

/**
 * @author Michał Podsiedzik
 */
public class BraveClientInInterceptor extends AbstractPhaseInterceptor<Message> {
    private final ClientResponseInterceptor responseInterceptor;

    public BraveClientInInterceptor(final ClientResponseInterceptor responseInterceptor) {
        super(Phase.RECEIVE);
        addBefore(StaxInInterceptor.class.getName());

        this.responseInterceptor = responseInterceptor;
    }

    @Override
    public void handleMessage(final Message message) throws Fault {
        responseInterceptor.handle(new HttpClientResponseAdapter(new ClientResponse(message)));
    }
}