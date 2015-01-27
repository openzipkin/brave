package com.github.kristofa.brave.cxf;

import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.client.ClientRequestInterceptor;
import com.github.kristofa.brave.client.spanfilter.SpanNameFilter;
import com.google.common.base.Optional;
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

    private final ClientRequestInterceptor clientRequestInterceptor;
    private final Optional<String> serviceName;
    private final ServerTracer serverTracer;

    public OutZipkinInterceptor(final ClientTracer clientTracer, final ServerTracer serverTracer,
                                final Optional<String> serviceName,
                                final Optional<SpanNameFilter> spanNameFilter) {
        super(Phase.POST_LOGICAL);
        Validate.notNull(serverTracer);
        this.serverTracer = serverTracer;
        org.apache.commons.lang.Validate.notNull(clientTracer);
        org.apache.commons.lang.Validate.notNull(serviceName);
        clientRequestInterceptor = new ClientRequestInterceptor(clientTracer, spanNameFilter);
        this.serviceName = serviceName;
    }

    public OutZipkinInterceptor(final ClientTracer clientTracer,  final ServerTracer serverTracer,
                                final Optional<String> serviceName,
                                final SpanNameFilter spanNameFilter) {
        this(clientTracer, serverTracer, serviceName, Optional.of(spanNameFilter));
    }

    public OutZipkinInterceptor(final ClientTracer clientTracer,  final ServerTracer serverTracer,
                                final Optional<String> serviceName) {
        this(clientTracer, serverTracer, serviceName, Optional.<SpanNameFilter>absent());

    }

    @Override
    public void handleMessage(Message message) throws Fault {
        if (isRequestor(message)) {
            clientRequestInterceptor.handle(new CXFRequestAdapter(message), serviceName);
            return;
        }
        LOG.debug("Sending server send.");
        try {
            serverTracer.setServerSend();
        } finally {
            serverTracer.clearCurrentSpan();
        }
    }
}
