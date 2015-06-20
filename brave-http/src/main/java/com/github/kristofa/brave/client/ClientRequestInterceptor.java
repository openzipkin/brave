package com.github.kristofa.brave.client;

import org.apache.commons.lang3.Validate;

import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.client.spanfilter.SpanNameFilter;

import javax.annotation.Nullable;

/**
 * Intercepts a client request and takes care of tracing the request. It will decide if we need to trace current request and
 * if so CS annotation will be submitted. You will also have to use {@link ClientResponseInterceptor} which will set CR
 * annotation.
 * <p/>
 * Abstraction on top of {@link ClientTracer}. If you want to integrate a library with brave it is advised to use this
 * abstraction instead of ClientTracer directly.
 * </p>
 * You will have to implement your own ClientRequestAdapter to provide necessary context.
 *
 * @see ClientRequestAdapter
 * @see ClientResponseInterceptor
 */
public class ClientRequestInterceptor {

    private final ClientTracer clientTracer;
    private static final String REQUEST_ANNOTATION = "request";
    private final SpanNameFilter spanNameFilter;

    public ClientRequestInterceptor(final ClientTracer clientTracer, final SpanNameFilter spanNameFilter) {
        Validate.notNull(clientTracer);
        this.clientTracer = clientTracer;
        this.spanNameFilter = spanNameFilter;
    }

    /**
     * Handles a client request.
     *
     * @param clientRequestAdapter Provides context about the request.
     * @param serviceNameOverride Nullable name of the service the client request calls. In case it is not specified the name
     *            will be derived from the URI of the request. It is important the used service name should be same on client
     *            as on server side.
     */
    public void handle(final ClientRequestAdapter clientRequestAdapter, @Nullable final String serviceNameOverride) {
        final String spanName = getSpanName(clientRequestAdapter, serviceNameOverride);
        final SpanId newSpanId = clientTracer.startNewSpan(spanName);
        ClientRequestHeaders.addTracingHeaders(clientRequestAdapter, newSpanId, spanName);
        final String serviceName = getServiceName(clientRequestAdapter, serviceNameOverride);
        if (serviceName != null) {
            clientTracer.setCurrentClientServiceName(serviceName);
        }
        clientTracer.submitBinaryAnnotation(REQUEST_ANNOTATION, clientRequestAdapter.getMethod() + " "
            + clientRequestAdapter.getUri());
        clientTracer.setClientSent();
    }

    private String getServiceName(final ClientRequestAdapter clientRequestAdapter, final String serviceNameOverride) {
        String serviceName = null;
        if (serviceNameOverride != null) {
            serviceName = serviceNameOverride;
        } else {
            final String path = clientRequestAdapter.getUri().getPath();
            final String[] split = path.split("/");
            if (split.length > 2 && path.startsWith("/")) {
                // If path starts with '/', then context is between first two '/'. Left over is path for service.
                final int contextPathSeparatorIndex = path.indexOf("/", 1);
                serviceName = path.substring(1, contextPathSeparatorIndex);
            }
        }
        return serviceName;
    }

    private String getSpanName(final ClientRequestAdapter clientRequestAdapter, final String serviceNameOverride) {
        String spanName;
        final String spanNameFromRequest = clientRequestAdapter.getSpanName();
        final String path = clientRequestAdapter.getUri().getPath();
        if (spanNameFromRequest != null) {
            return spanNameFromRequest;
        } else if (serviceNameOverride != null) {
            spanName = path;
        } else {
            final String[] split = path.split("/");
            if (split.length > 2 && path.startsWith("/")) {
                // If path starts with '/', then context is between first two '/'. Left over is path for service.
                final int contextPathSeparatorIndex = path.indexOf("/", 1);
                spanName = path.substring(contextPathSeparatorIndex);
            } else {
                spanName = path;
            }
        }
        if (spanNameFilter != null) {
            spanName = spanNameFilter.filterSpanName(spanName);
        }
        return spanName;
    }
}