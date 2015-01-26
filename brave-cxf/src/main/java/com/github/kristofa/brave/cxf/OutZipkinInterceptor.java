package com.github.kristofa.brave.cxf;

import com.github.kristofa.brave.ServerTracer;
import org.apache.commons.lang3.Validate;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: fedor
 * Date: 12.01.2015
 * Time: 17:04
 */
public class OutZipkinInterceptor extends AbstractPhaseInterceptor<Message> {
    private static final Logger LOG = LoggerFactory.getLogger(OutZipkinInterceptor.class);

    private final ServerTracer serverTracer;

    public OutZipkinInterceptor(final ServerTracer serverTracer) {
        super(Phase.POST_LOGICAL);
        Validate.notNull(serverTracer);
        this.serverTracer = serverTracer;
    }

    @Override
    public void handleMessage(Message message) throws Fault {

        LOG.debug("Sending server send.");
        try {
            serverTracer.setServerSend();
        } finally {
            serverTracer.clearCurrentSpan();
        }
    }

}
