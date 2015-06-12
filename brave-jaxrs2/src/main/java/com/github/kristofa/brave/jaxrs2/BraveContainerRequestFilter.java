package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.EndPointSubmitter;
import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.ServerTracer;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Provider;

import java.io.IOException;
import java.net.URI;

/**
 * Intercepts incoming container requests and extracts any trace information from the request header
 * Also sends sr annotations.
 */
@Provider
public class BraveContainerRequestFilter implements ContainerRequestFilter {

    private static Logger logger = LoggerFactory.getLogger(BraveContainerRequestFilter.class);

    private final ServerTracer serverTracer;
    private final EndPointSubmitter endPointSubmitter;

    @Inject
    public BraveContainerRequestFilter(ServerTracer serverTracer, EndPointSubmitter endPointSubmitter) {
        this.serverTracer = serverTracer;
        this.endPointSubmitter = endPointSubmitter;
    }

    @Override
    public void filter(ContainerRequestContext containerRequestContext) throws IOException {
        serverTracer.clearCurrentSpan();
        UriInfo uriInfo = containerRequestContext.getUriInfo();
        submitEndpoint(uriInfo);

        TraceData traceData = getTraceDataFromHeaders(containerRequestContext);
        if (Boolean.FALSE.equals(traceData.shouldBeTraced())) {
            serverTracer.setStateNoTracing();
            logger.debug("Not tracing request");
        } else {
            String spanName = getSpanName(traceData, uriInfo);
            if (traceData.getTraceId() != null && traceData.getSpanId() != null) {
                logger.debug("Received span information as part of request");
                serverTracer.setStateCurrentTrace(traceData.getTraceId(), traceData.getSpanId(),
                        traceData.getParentSpanId(), spanName);
            } else {
                logger.debug("Received no span state");
                serverTracer.setStateUnknown(spanName);
            }
        }
        serverTracer.setServerReceived();
    }

    private void submitEndpoint(UriInfo uri) {
        if (!endPointSubmitter.endPointSubmitted()) {
            URI baseUri = uri.getBaseUri();
            String contextPath = getContextPath(uri);
            String localAddr = baseUri.getHost();
            int localPort = baseUri.getPort();
            endPointSubmitter.submit(localAddr, localPort, contextPath);
            logger.debug("Setting endpoint: addr: {}, port: {}, contextpath: {}", localAddr, localPort, contextPath);
        }
    }

    private String getContextPath(UriInfo uri) {
        final String contextPath = uri.getAbsolutePath().getPath();
        String[] parts = StringUtils.split(contextPath, "/");
        return parts[0];
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
