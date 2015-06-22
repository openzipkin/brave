package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.EndpointSubmitter;
import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.ServerTracer;

import java.io.IOException;
import java.net.URI;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Provider;

import static java.lang.String.format;

/**
 * Intercepts incoming container requests and extracts any trace information from the request header
 * Also sends sr annotations.
 */
@Provider
public class BraveContainerRequestFilter implements ContainerRequestFilter {

    private static Logger logger = Logger.getLogger(BraveContainerRequestFilter.class.getName());

    private final ServerTracer serverTracer;
    private final EndpointSubmitter endpointSubmitter;

    @Inject
    public BraveContainerRequestFilter(ServerTracer serverTracer, EndpointSubmitter endpointSubmitter) {
        this.serverTracer = serverTracer;
        this.endpointSubmitter = endpointSubmitter;
    }

    @Override
    public void filter(ContainerRequestContext containerRequestContext) throws IOException {
        serverTracer.clearCurrentSpan();
        UriInfo uriInfo = containerRequestContext.getUriInfo();
        submitEndpoint(uriInfo);

        TraceData traceData = getTraceDataFromHeaders(containerRequestContext);
        if (Boolean.FALSE.equals(traceData.shouldBeTraced())) {
            serverTracer.setStateNoTracing();
            logger.fine("Not tracing request");
        } else {
            String spanName = getSpanName(traceData, uriInfo);
            if (traceData.getTraceId() != null && traceData.getSpanId() != null) {
                logger.fine("Received span information as part of request");
                serverTracer.setStateCurrentTrace(traceData.getTraceId(), traceData.getSpanId(),
                        traceData.getParentSpanId(), spanName);
            } else {
                logger.fine("Received no span state");
                serverTracer.setStateUnknown(spanName);
            }
        }
        serverTracer.setServerReceived();
    }

    private void submitEndpoint(UriInfo uri) {
        if (!endpointSubmitter.endpointSubmitted()) {
            URI baseUri = uri.getBaseUri();
            String contextPath = getContextPath(uri.getAbsolutePath().getPath());
            String localAddr = baseUri.getHost();
            int localPort = baseUri.getPort();
            endpointSubmitter.submit(localAddr, localPort, contextPath);
            logger.fine(format("Setting endpoint: addr: %s, port: %s, contextpath: %s", localAddr, localPort, contextPath));
        }
    }

    static String getContextPath(String absolutePath) {
        StringBuilder contextPath = new StringBuilder(absolutePath);
        // Delete any leading slashes
        while (contextPath.charAt(0) == '/') {
            contextPath.deleteCharAt(0);
        }
        int slashIndex = contextPath.indexOf("/");
        return slashIndex == -1 ? contextPath.toString() : contextPath.substring(0, slashIndex);
    }

    private String getSpanName(TraceData traceData, UriInfo uri) {
        if (traceData.getSpanName() == null || traceData.getSpanName().isEmpty()) {
            return uri.getAbsolutePath().getPath();
        }
        return traceData.getSpanName();
    }

    private TraceData getTraceDataFromHeaders(ContainerRequestContext request) {
        TraceData traceData = new TraceData();
        traceData.setTraceId(longOrNull(request.getHeaderString(BraveHttpHeaders.TraceId.getName())));
        traceData.setSpanId(longOrNull(request.getHeaderString(BraveHttpHeaders.SpanId.getName())));
        traceData.setParentSpanId(longOrNull(request.getHeaderString(BraveHttpHeaders.ParentSpanId.getName())));
        traceData.setShouldBeSampled(nullOrBoolean(request.getHeaderString(BraveHttpHeaders.Sampled.getName())));
        traceData.setSpanName(request.getHeaderString(BraveHttpHeaders.SpanName.getName()));
        return traceData;
    }

    private Boolean nullOrBoolean(String value) {
        return (value == null) ? null : Boolean.valueOf(value);
    }

    private Long longOrNull(String value) {
        if (value == null) {
            return null;
        }
        return IdConversion.convertToLong(value);
    }
}
