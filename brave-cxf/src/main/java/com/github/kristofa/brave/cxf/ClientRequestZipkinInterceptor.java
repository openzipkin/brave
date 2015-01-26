package com.github.kristofa.brave.cxf;

import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.client.ClientRequestInterceptor;
import com.github.kristofa.brave.client.spanfilter.SpanNameFilter;
import com.google.common.base.Optional;
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

public class ClientRequestZipkinInterceptor extends AbstractPhaseInterceptor<Message> {
    private static final Logger LOG = LoggerFactory.getLogger(ClientRequestZipkinInterceptor.class);

    private final ClientRequestInterceptor clientRequestInterceptor;
    private final Optional<String> serviceName;

    /**
     * Creates a new instance.
     *
     * @param clientTracer ClientTracer should not be <code>null</code>.
     * @param serviceName Optional Service Name override
     * @param spanNameFilter
     */
    public ClientRequestZipkinInterceptor(final ClientTracer clientTracer, final Optional<String> serviceName,
                                       final SpanNameFilter spanNameFilter) {
        this(clientTracer, serviceName, Optional.of(spanNameFilter));
    }

    /**
     * Creates a new instance.
     *
     * @param clientTracer ClientTracer should not be <code>null</code>.
     * @param serviceName Optional Service Name override
     */
    public ClientRequestZipkinInterceptor(final ClientTracer clientTracer, final Optional<String> serviceName) {
        this(clientTracer, serviceName, Optional.<SpanNameFilter>absent());

    }

    /**
     * Private constructor, creates a new instance.
     *
     * @param clientTracer ClientTracer should not be <code>null</code>.
     * @param serviceName Optional Service Name override
     * @param spanNameFilter optional {@link SpanNameFilter}
     */
    private ClientRequestZipkinInterceptor(final ClientTracer clientTracer, final Optional<String> serviceName,
                                        final Optional<SpanNameFilter> spanNameFilter) {
        super(Phase.PRE_LOGICAL);
        org.apache.commons.lang.Validate.notNull(clientTracer);
        org.apache.commons.lang.Validate.notNull(serviceName);
        clientRequestInterceptor = new ClientRequestInterceptor(clientTracer, spanNameFilter);
        this.serviceName = serviceName;
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        clientRequestInterceptor.handle(new CXFRequestAdapter(message), serviceName);

    }
}
