package com.github.kristofa.brave.resteasy;

import javax.ws.rs.ext.Provider;

import org.apache.commons.lang3.Validate;
import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.interception.PostProcessInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.kristofa.brave.ServerTracer;

/**
 * Rest Easy {@link PostProcessInterceptor} that will submit server send state.
 * 
 * @author kristof
 */
@Component
@Provider
@ServerInterceptor
public class BravePostProcessInterceptor implements PostProcessInterceptor {

    private final static Logger LOGGER = LoggerFactory.getLogger(BravePostProcessInterceptor.class);

    private final ServerTracer serverTracer;

    /**
     * Creates a new instance.
     * 
     * @param serverTracer {@link ServerTracer}. Should not be null.
     */
    @Autowired
    public BravePostProcessInterceptor(final ServerTracer serverTracer) {
        Validate.notNull(serverTracer);
        this.serverTracer = serverTracer;
    }

    @Override
    public void postProcess(final ServerResponse response) {
        // We can submit this in any case. When server state is not set or
        // we should not trace this request nothing will happen.
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Sending server send.");
        }
        serverTracer.setServerSend();
    }

}
