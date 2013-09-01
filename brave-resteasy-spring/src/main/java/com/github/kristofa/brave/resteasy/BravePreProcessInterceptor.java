package com.github.kristofa.brave.resteasy;

import java.util.List;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ResourceMethod;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.Failure;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.interception.PreProcessInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.EndPointSubmitter;
import com.github.kristofa.brave.ServerTracer;

/**
 * Rest Easy {@link PreProcessInterceptor} that will:
 * <ol>
 * <li>Set {@link EndPoint} information for our service in case it is not set yet.</li>
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

    private final static Logger LOGGER = LoggerFactory.getLogger(BravePreProcessInterceptor.class);

    private final EndPointSubmitter endPointSubmitter;
    private final ServerTracer serverTracer;

    @Context
    HttpServletRequest servletRequest;

    /**
     * Creates a new instance.
     * 
     * @param endPointSubmitter {@link EndPointSubmitter}. Should not be <code>null</code>.
     * @param serverTracer {@link ServerTracer}. Should not be <code>null</code>.
     * @param randomGenerator Random generator.
     */
    @Autowired
    public BravePreProcessInterceptor(final EndPointSubmitter endPointSubmitter, final ServerTracer serverTracer) {
        Validate.notNull(endPointSubmitter);
        Validate.notNull(serverTracer);
        this.endPointSubmitter = endPointSubmitter;
        this.serverTracer = serverTracer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServerResponse preProcess(final HttpRequest request, final ResourceMethod method) throws Failure,
        WebApplicationException {

        if (!endPointSubmitter.endPointSubmitted()) {
            final String localAddr = servletRequest.getLocalAddr();
            final int localPort = servletRequest.getLocalPort();
            final String contextPath = servletRequest.getContextPath();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Setting endpoint: addr: " + localAddr + ", port: " + localPort + ", contextpath: "
                    + contextPath);
            }
            endPointSubmitter.submit(localAddr, localPort, contextPath);
        }

        serverTracer.clearCurrentSpan();
        final TraceData traceData = getTraceData(request);

        if (Boolean.FALSE.equals(traceData.shouldBeTraced())) {
            serverTracer.setStateNoTracing();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Received indication that we should NOT trace.");
            }
        } else {
            String spanName = null;
            if (StringUtils.isNotBlank(traceData.getSpanName())) {
                spanName = traceData.getSpanName();
            } else {
                spanName = request.getPreprocessedPath();
            }
            if (traceData.getTraceId() != null && traceData.getSpanId() != null) {

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Received span information as part of request.");
                }
                serverTracer.setStateExistingTrace(traceData.getTraceId(), traceData.getSpanId(),
                    traceData.getParentSpanId(), spanName);
            } else {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Received no span state.");
                }
                serverTracer.setStateUnknown(spanName);
            }
            serverTracer.setServerReceived();
        }
        return null;
    }

    private TraceData getTraceData(final HttpRequest request) {
        final HttpHeaders httpHeaders = request.getHttpHeaders();
        final MultivaluedMap<String, String> requestHeaders = httpHeaders.getRequestHeaders();

        final TraceData traceData = new TraceData();

        for (final Entry<String, List<String>> headerEntry : requestHeaders.entrySet()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(headerEntry.getKey() + "=" + headerEntry.getValue());
            }
            if (BraveHttpHeaders.TraceId.getName().equalsIgnoreCase(headerEntry.getKey())) {
                traceData.setTraceId(getFirstLongValueFor(headerEntry));
            } else if (BraveHttpHeaders.SpanId.getName().equalsIgnoreCase(headerEntry.getKey())) {
                traceData.setSpanId(getFirstLongValueFor(headerEntry));
            } else if (BraveHttpHeaders.ParentSpanId.getName().equalsIgnoreCase(headerEntry.getKey())) {
                traceData.setParentSpanId(getFirstLongValueFor(headerEntry));
            } else if (BraveHttpHeaders.Sampled.getName().equalsIgnoreCase(headerEntry.getKey())) {
                traceData.setShouldBeSampled(getFirstBooleanValueFor(headerEntry));
            } else if (BraveHttpHeaders.SpanName.getName().equalsIgnoreCase(headerEntry.getKey())) {
                traceData.setSpanName(getFirstStringValueFor(headerEntry));
            }
        }
        return traceData;
    }

    private Long getFirstLongValueFor(final Entry<String, List<String>> headerEntry) {

        final String firstStringValueFor = getFirstStringValueFor(headerEntry);
        return firstStringValueFor == null ? null : Long.valueOf(firstStringValueFor);

    }

    private Boolean getFirstBooleanValueFor(final Entry<String, List<String>> headerEntry) {
        final String firstStringValueFor = getFirstStringValueFor(headerEntry);
        return firstStringValueFor == null ? null : Boolean.valueOf(firstStringValueFor);
    }

    private String getFirstStringValueFor(final Entry<String, List<String>> headerEntry) {
        final List<String> values = headerEntry.getValue();
        if (values != null && values.size() > 0) {
            return headerEntry.getValue().get(0);
        }
        return null;
    }

}
