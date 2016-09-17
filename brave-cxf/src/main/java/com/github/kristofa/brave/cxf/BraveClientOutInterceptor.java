package com.github.kristofa.brave.cxf;

import static com.github.kristofa.brave.cxf.BraveCxfConstants.BRAVE_CLIENT_SPAN;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;

/**
 * @author Michał Podsiedzik
 */
public class BraveClientOutInterceptor extends AbstractPhaseInterceptor<Message> {
    private final Brave brave;
    private final SpanNameProvider spanNameProvider;

    public BraveClientOutInterceptor(final Brave brave, final SpanNameProvider spanNameProvider) {
        super(Phase.PRE_STREAM);
        addBefore(LoggingOutInterceptor.class.getName());

        this.brave = brave;
        this.spanNameProvider = spanNameProvider;
    }

    @Override
    public void handleMessage(final Message message) throws Fault {
        try {
            brave.clientRequestInterceptor().handle(new HttpClientRequestAdapter(new ClientRequest(message), spanNameProvider));
            message.getExchange().put(BRAVE_CLIENT_SPAN, brave.clientSpanThreadBinder().getCurrentClientSpan());
        } finally {
            brave.clientSpanThreadBinder().setCurrentSpan(null);
        }
    }
}
