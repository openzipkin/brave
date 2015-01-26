package com.github.kristofa.brave.cxf;

import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.client.ClientResponseInterceptor;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: fedor
 * Date: 17.01.2015
 */
public class ClientResponseZipkinInterceptor extends AbstractPhaseInterceptor<Message> {
    private static final Logger LOG = LoggerFactory.getLogger(ClientResponseZipkinInterceptor.class);

    private final ClientResponseInterceptor traceResponseBuilder;

    /**
     * Create a new instance.
     *
     * @param clientTracer ClientTracer. Should not be <code>null</code>.
     */
    public ClientResponseZipkinInterceptor(final ClientTracer clientTracer) {
        super(Phase.INVOKE);
        org.apache.commons.lang.Validate.notNull(clientTracer);
        this.traceResponseBuilder = new ClientResponseInterceptor(clientTracer);
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        traceResponseBuilder.handle(new CXFClientResponseAdapter(message));
    }
}
