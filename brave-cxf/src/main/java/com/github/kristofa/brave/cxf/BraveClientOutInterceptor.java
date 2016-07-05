package com.github.kristofa.brave.cxf;

import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

/**
 * @author Michał Podsiedzik
 */
public class BraveClientOutInterceptor extends AbstractPhaseInterceptor<Message> {
    private final ClientRequestInterceptor requestInterceptor;
    private final SpanNameProvider spanNameProvider;

    public BraveClientOutInterceptor(final SpanNameProvider spanNameProvider, final ClientRequestInterceptor requestInterceptor) {
        super(Phase.PRE_STREAM);
        addBefore(LoggingOutInterceptor.class.getName());

        this.requestInterceptor = requestInterceptor;
        this.spanNameProvider = spanNameProvider;
    }

    @Override
    public void handleMessage(final Message message) throws Fault {
        requestInterceptor.handle(new HttpClientRequestAdapter(new ClientRequest(message), spanNameProvider));
    }
}
