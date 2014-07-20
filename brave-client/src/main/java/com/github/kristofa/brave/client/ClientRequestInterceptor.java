package com.github.kristofa.brave.client;

import com.github.kristofa.brave.ClientRequestAdapter;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.SpanId;
import com.google.common.base.Optional;
import org.apache.commons.lang.Validate;

public class ClientRequestInterceptor {
    private final ClientTracer clientTracer;
    private static final String REQUEST_ANNOTATION = "request";

    public ClientRequestInterceptor(ClientTracer clientTracer) {
        Validate.notNull(clientTracer);
        this.clientTracer = clientTracer;
    }

    public void handle(ClientRequestAdapter clientRequestAdapter, Optional<String> serviceNameOverride) {
        String spanName = getSpanName(clientRequestAdapter, serviceNameOverride);
        final SpanId newSpanId = clientTracer.startNewSpan(spanName);
        ClientRequestHeaders.addTracingHeaders(clientRequestAdapter, newSpanId);
        Optional<String> serviceName = getServiceName(clientRequestAdapter, serviceNameOverride);
        if (serviceName.isPresent()) {
            clientTracer.setCurrentClientServiceName(serviceName.get());
        }
        clientTracer.submitBinaryAnnotation(REQUEST_ANNOTATION, clientRequestAdapter.getMethod() + " " + clientRequestAdapter.getUri());
        clientTracer.setClientSent();
    }

    private Optional<String> getServiceName(ClientRequestAdapter clientRequestAdapter, Optional<String> serviceNameOverride) {
        Optional<String> serviceName;
        if(serviceNameOverride.isPresent()) {
            serviceName = serviceNameOverride;
        } else {
            String path = clientRequestAdapter.getUri().getPath();
            final String[] split = path.split("/");
            if (split.length > 2 && path.startsWith("/")) {
                // If path starts with '/', then context is between first two '/'. Left over is path for service.
                final int contextPathSeparatorIndex = path.indexOf("/", 1);
                serviceName = Optional.of(path.substring(1, contextPathSeparatorIndex));
            } else {
                serviceName = Optional.absent();
            }
        }
        return serviceName;
    }

    private String getSpanName(ClientRequestAdapter clientRequestAdapter, Optional<String> serviceNameOverride) {
        String spanName;
        if(serviceNameOverride.isPresent()) {
            if (clientRequestAdapter.getSpanName().isPresent()) {
                spanName = clientRequestAdapter.getSpanName().get();
            } else {
                spanName = clientRequestAdapter.getUri().getPath();
            }
        } else {
            final String path = clientRequestAdapter.getUri().getPath();
            final String[] split = path.split("/");
            if (split.length > 2 && path.startsWith("/")) {
                // If path starts with '/', then context is between first two '/'. Left over is path for service.
                final int contextPathSeparatorIndex = path.indexOf("/", 1);
                spanName = path.substring(contextPathSeparatorIndex);
            } else {
                spanName = path;
            }
        }
        return spanName;
    }
}
