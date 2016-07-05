package com.github.kristofa.brave.cxf;

import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.http.HttpServerRequestAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.StaxInInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

/**
 * @author Michał Podsiedzik
 */
public class BraveServerInInterceptor extends AbstractPhaseInterceptor<Message> {
    private final ServerRequestInterceptor requestInterceptor;
    private final SpanNameProvider spanNameProvider;

    public BraveServerInInterceptor(final ServerRequestInterceptor interceptor, final SpanNameProvider spanNameProvider) {
        super(Phase.RECEIVE);
        addBefore(StaxInInterceptor.class.getName());

        this.requestInterceptor = interceptor;
        this.spanNameProvider = spanNameProvider;
    }

    @Override
    public void handleMessage(final Message message) throws Fault {
        requestInterceptor.handle(new HttpServerRequestAdapter(new ServerRequest(message), spanNameProvider));
    }
}