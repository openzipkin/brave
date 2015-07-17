package com.github.kristofa.brave.resteasy;

import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;

import com.github.kristofa.brave.*;
import com.github.kristofa.brave.http.HttpServerRequest;
import com.github.kristofa.brave.http.HttpServerRequestAdapter;
import com.github.kristofa.brave.http.ServiceNameProvider;
import com.github.kristofa.brave.http.SpanNameProvider;
import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ResourceMethod;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.Failure;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.interception.PreProcessInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.twitter.zipkin.gen.Endpoint;

import static com.github.kristofa.brave.internal.Util.checkNotNull;
import static java.lang.String.format;

/**
 * Rest Easy {@link PreProcessInterceptor} that will:
 * <ol>
 * <li>Set {@link Endpoint} information for our service in case it is not set yet.</li>
 * <li>Get trace data (trace id, span id, parent span id) from http headers and initialize state for request + submit 'server
 * received' for request.</li>
 * <li>If no trace information is submitted we will start a new span. In that case it means client does not support tracing
 * and should be adapted.</li>
 * </ol>
 *
 * @author kristof
 */
@Component
@Provider
@ServerInterceptor
public class BravePreProcessInterceptor implements PreProcessInterceptor {

    private final static Logger LOGGER = Logger.getLogger(BravePreProcessInterceptor.class.getName());

    private final EndpointSubmitter endpointSubmitter;
    private final ServerRequestInterceptor reqInterceptor;
    private final ServerResponseInterceptor resInterceptor;
    private final SpanNameProvider spanNameProvider;
    private final ServiceNameProvider serviceNameProvider;

    @Context
    HttpServletRequest servletRequest;

    /**
     * Creates a new instance.
     *
     * @param endpointSubmitter {@link EndpointSubmitter}. Should not be <code>null</code>.
     * @param reqInterceptor Request interceptor.
     * @param resInterceptor ResponseInterceptor.
     * @param spanNameProvider Span name provider.
     * @param serviceNameProvider Service name provider.
     */
    @Autowired
    public BravePreProcessInterceptor(final EndpointSubmitter endpointSubmitter,
                                      ServerRequestInterceptor reqInterceptor,
                                      ServerResponseInterceptor resInterceptor,
                                      SpanNameProvider spanNameProvider,
                                      ServiceNameProvider serviceNameProvider
    ) {
        this.endpointSubmitter = endpointSubmitter;
        this.reqInterceptor = reqInterceptor;
        this.resInterceptor = resInterceptor;
        this.spanNameProvider = spanNameProvider;
        this.serviceNameProvider = serviceNameProvider;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServerResponse preProcess(final HttpRequest request, final ResourceMethod method) throws Failure,
        WebApplicationException {

        HttpServerRequest req = new RestEasyHttpServerRequest(request);
        submitEndpoint(req);


        HttpServerRequestAdapter reqAdapter = new HttpServerRequestAdapter(req, spanNameProvider);
        reqInterceptor.handle(reqAdapter);

        return null;
    }

    private void submitEndpoint(HttpServerRequest request) {
        if (!endpointSubmitter.endpointSubmitted()) {
            final String localAddr = servletRequest.getLocalAddr();
            final int localPort = servletRequest.getLocalPort();
            final String serviceName = serviceNameProvider.serviceName(request);
            LOGGER.fine(format("Setting endpoint: addr: %s, port: %s, serviceName: %s", localAddr, localPort, serviceName));
            endpointSubmitter.submit(localAddr, localPort, serviceName);
        }
    }


}
