package com.github.kristofa.brave.resteasy;

import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
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


/**
 * Rest Easy {@link PreProcessInterceptor} that will:
 * <ol>
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

    private final ServerRequestInterceptor reqInterceptor;
    private final SpanNameProvider spanNameProvider;

    @Context
    HttpServletRequest servletRequest;

    /**
     * Creates a new instance.
     *
     * @param reqInterceptor Request interceptor.
     * @param spanNameProvider Span name provider.
     */
    @Autowired
    public BravePreProcessInterceptor(ServerRequestInterceptor reqInterceptor,
                                      SpanNameProvider spanNameProvider
    ) {
        this.reqInterceptor = reqInterceptor;
        this.spanNameProvider = spanNameProvider;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServerResponse preProcess(final HttpRequest request, final ResourceMethod method) throws Failure,
        WebApplicationException {

        HttpServerRequest req = new RestEasyHttpServerRequest(request);
        HttpServerRequestAdapter reqAdapter = new HttpServerRequestAdapter(req, spanNameProvider);
        reqInterceptor.handle(reqAdapter);
        return null;
    }

}
