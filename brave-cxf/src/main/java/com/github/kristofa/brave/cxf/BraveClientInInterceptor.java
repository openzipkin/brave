package com.github.kristofa.brave.cxf;

import static com.github.kristofa.brave.cxf.BraveCxfConstants.BRAVE_CLIENT_SPAN;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.StaxInInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.http.HttpClientResponseAdapter;
import com.twitter.zipkin.gen.Span;

/**
 * @author Michał Podsiedzik
 */
public class BraveClientInInterceptor extends AbstractPhaseInterceptor<Message> {
    private final Brave brave;

    public BraveClientInInterceptor(final Brave brave) {
        super(Phase.RECEIVE);
        addBefore(StaxInInterceptor.class.getName());

        this.brave = brave;
    }

    @Override
    public void handleMessage(final Message message) throws Fault {
        final Span span = (Span) message.getExchange().get(BRAVE_CLIENT_SPAN);
        if (span != null) {
            brave.clientSpanThreadBinder().setCurrentSpan(span);
            brave.clientResponseInterceptor().handle(new HttpClientResponseAdapter(new ClientResponse(message)));
        }
    }
}