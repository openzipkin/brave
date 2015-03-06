package com.github.kristofa.brave.cxf;

import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.client.ClientRequestInterceptor;
import com.github.kristofa.brave.client.spanfilter.SpanNameFilter;
import com.google.common.base.Optional;
import org.apache.commons.lang.Validate;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * User: fedor
 * Date: 12.01.2015
 * Time: 17:04
 */
public class OutZipkinInterceptor extends AbstractPhaseInterceptor<Message> {
    private static final Logger LOG = LoggerFactory.getLogger(OutZipkinInterceptor.class);

    private final ZipkinConfig zipkinConfig;
    private final ClientRequestInterceptor clientRequestInterceptor;

    public OutZipkinInterceptor(final ZipkinConfig zipkinConfig) {
        super(Phase.POST_LOGICAL);

        Validate.notNull(zipkinConfig);
        this.zipkinConfig = zipkinConfig;
        clientRequestInterceptor = new ClientRequestInterceptor(zipkinConfig.getClientTracer(), zipkinConfig.getSpanNameFilter());
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        ServerTracer serverTracer = zipkinConfig.getServerTracer();
        AttemptLimiter attemptLimiter = zipkinConfig.getOutAttemptLimiter();
        if (!attemptLimiter.shouldTry())
            return;
        if (isRequestor(message)) {
            try {
                //If we are in IsRoot mode and root span is not yet initizalized
                zipkinConfig.RootStartCheck();

                //Here we send request to the server. The only way to get service name for "cs" is to parse URL
                SpanAddress spanAddress = new SpanAddress(URI.create((String) message.get(Message.REQUEST_URI)));
                clientRequestInterceptor.handle(new CXFRequestAdapter(message), Optional.fromNullable(spanAddress.getServiceName()));
                attemptLimiter.reportSuccess();
            } catch (Exception e) {
                attemptLimiter.reportFailure();
                LOG.error("Zipkin interception exception", e);
            }
            return;
        }
        // root request mode
        if (zipkinConfig.IsRoot())
            return;
        LOG.debug("Sending server send.");
        try {
            serverTracer.setServerSend();
        } finally {
            serverTracer.clearCurrentSpan();
        }
        attemptLimiter.reportSuccess();
    }
}