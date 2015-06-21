package com.github.kristofa.brave.jersey;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.EndpointSubmitter;
import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.ServerTracer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;

import java.io.IOException;

/**
 * Servlet filter that will extract trace headers from the request and send
 * sr (server received) and ss (server sent) annotations.
 */
@Singleton
public class ServletTraceFilter implements Filter {

    private static Logger logger = LoggerFactory.getLogger(ServletTraceFilter.class);
    private final ServerTracer serverTracer;
    private final EndpointSubmitter endpointSubmitter;

    @Inject
    public ServletTraceFilter(ServerTracer serverTracer, EndpointSubmitter endpointSubmitter) {
        this.serverTracer = serverTracer;
        this.endpointSubmitter = endpointSubmitter;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        boolean traceEstablished = tryEstablishServerTrace(request);

        chain.doFilter(request, response);

        if (traceEstablished) {
            finalizeServerTrace();
        }
    }

    /**
     * Clear current span and perhaps start a new one.  We do this all in a try/catch so that
     * we are safe by default.
     *
     * @return true if we were able to establish a trace without exception
     */
    private boolean tryEstablishServerTrace(ServletRequest request) {
        try {
            serverTracer.clearCurrentSpan();
            if (request instanceof HttpServletRequest) {
                final HttpServletRequest httpServletRequest = (HttpServletRequest) request;
                submitEndpoint(httpServletRequest);

                TraceData traceData = getTraceDataFromHeaders(httpServletRequest);
                if (Boolean.FALSE.equals(traceData.shouldBeTraced())) {
                    serverTracer.setStateNoTracing();
                    logger.debug("Not tracing request");
                } else {
                    String spanName = getSpanName(traceData, httpServletRequest);
                    if (traceData.getTraceId() != null && traceData.getSpanId() != null) {
                        logger.debug("Received span information as part of request");
                        serverTracer.setStateCurrentTrace(traceData.getTraceId(), traceData.getSpanId(),
                                traceData.getParentSpanId(), spanName);
                    } else {
                        logger.debug("Received no span state");
                        serverTracer.setStateUnknown(spanName);
                    }
                }
            }
            serverTracer.setServerReceived();
            return true;
        } catch (Exception e) {
            logger.warn("Exception establishing server trace", e);
            return false;
        }
    }

    private void finalizeServerTrace() {
        try {
            serverTracer.setServerSend();
        } catch (Exception e) {
            logger.warn("Exception finalizing server trace", e);
        }
    }

    private void submitEndpoint(HttpServletRequest request) {
        if (!endpointSubmitter.endpointSubmitted()) {
            String contextPath = request.getContextPath();
            String localAddr = request.getLocalAddr();
            int localPort = request.getLocalPort();
            endpointSubmitter.submit(localAddr, localPort, contextPath);
            logger.debug("Setting endpoint: addr: {}, port: {}, contextpath: {}", localAddr, localPort, contextPath);
        }
    }

    private String getSpanName(TraceData traceData, HttpServletRequest request) {
        if (traceData.getSpanName() == null || traceData.getSpanName().isEmpty()) {
            return request.getRequestURI();
        }
        return traceData.getSpanName();
    }

    private TraceData getTraceDataFromHeaders(HttpServletRequest httpRequest) {
        TraceData traceData = new TraceData();
        traceData.setTraceId(longOrNull(httpRequest.getHeader(BraveHttpHeaders.TraceId.getName())));
        traceData.setSpanId(longOrNull(httpRequest.getHeader(BraveHttpHeaders.SpanId.getName())));
        traceData.setParentSpanId(longOrNull(httpRequest.getHeader(BraveHttpHeaders.ParentSpanId.getName())));
        traceData.setShouldBeSampled(nullOrBoolean(httpRequest.getHeader(BraveHttpHeaders.Sampled.getName())));
        traceData.setSpanName(httpRequest.getHeader(BraveHttpHeaders.SpanName.getName()));
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

    @Override
    public void destroy() {
    }
}
