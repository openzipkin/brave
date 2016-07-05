package com.github.kristofa.brave.cxf;

import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.http.HttpServerResponseAdapter;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

/**
 * @author Michał Podsiedzik
 */
public class BraveServerOutInterceptor extends AbstractPhaseInterceptor<Message> {
    private final ServerResponseInterceptor responseInterceptor;

    public BraveServerOutInterceptor(final ServerResponseInterceptor responseInterceptor) {
        super(Phase.PRE_STREAM);
        addBefore(LoggingOutInterceptor.class.getName());

        this.responseInterceptor = responseInterceptor;
    }

    @Override
    public void handleMessage(final Message message) throws Fault {
        responseInterceptor.handle(new HttpServerResponseAdapter(new ServerResponse(message)));
    }
}