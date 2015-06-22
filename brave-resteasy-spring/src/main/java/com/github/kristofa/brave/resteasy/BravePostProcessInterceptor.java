package com.github.kristofa.brave.resteasy;

import java.util.logging.Logger;

import javax.ws.rs.ext.Provider;

import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.interception.PostProcessInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.kristofa.brave.ServerTracer;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Rest Easy {@link PostProcessInterceptor} that will submit server send state.
 * 
 * @author kristof
 */
@Component
@Provider
@ServerInterceptor
public class BravePostProcessInterceptor implements PostProcessInterceptor {

    private final static Logger LOGGER = Logger.getLogger(BravePostProcessInterceptor.class.getName());

    private final ServerTracer serverTracer;

    /**
     * Creates a new instance.
     * 
     * @param serverTracer {@link ServerTracer}. Should not be null.
     */
    @Autowired
    public BravePostProcessInterceptor(ServerTracer serverTracer) {
        this.serverTracer = checkNotNull(serverTracer, "Null serverTracer");
    }

    @Override
    public void postProcess(final ServerResponse response) {
        // We can submit this in any case. When server state is not set or
        // we should not trace this request nothing will happen.
        LOGGER.fine("Sending server send.");
        try {
            serverTracer.setServerSend();
        } finally {
            serverTracer.clearCurrentSpan();
        }
    }

}
