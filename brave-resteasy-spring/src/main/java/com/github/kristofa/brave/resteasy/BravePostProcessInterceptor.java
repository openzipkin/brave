package com.github.kristofa.brave.resteasy;

import java.util.logging.Logger;

import javax.ws.rs.ext.Provider;

import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.http.HttpResponse;
import com.github.kristofa.brave.http.HttpServerResponseAdapter;
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

    private final ServerResponseInterceptor respInterceptor;

    /**
     * Creates a new instance.
     * 
     * @param respInterceptor {@link ServerTracer}. Should not be null.
     */
    @Autowired
    public BravePostProcessInterceptor(ServerResponseInterceptor respInterceptor) {
        this.respInterceptor = respInterceptor;
    }

    @Override
    public void postProcess(final ServerResponse response) {

        HttpResponse httpResponse = new HttpResponse() {

            @Override
            public int getHttpStatusCode() {
                return response.getStatus();
            }
        };
        HttpServerResponseAdapter adapter = new HttpServerResponseAdapter(httpResponse);
        respInterceptor.handle(adapter);
    }

}
