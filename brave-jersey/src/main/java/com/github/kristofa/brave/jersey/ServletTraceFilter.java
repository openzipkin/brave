package com.github.kristofa.brave.jersey;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.EndPointSubmitter;
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
    private final EndPointSubmitter endPointSubmitter;

    @Inject
    public ServletTraceFilter(ServerTracer serverTracer, EndPointSubmitter endPointSubmitter) {
        this.serverTracer = serverTracer;
        this.endPointSubmitter = endPointSubmitter;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        serverTracer.clearCurrentSpan();
        if (request instanceof HttpServletRequest) {
            submitEndpoint((HttpServletRequest) request);

            TraceData traceData = getTraceDataFromHeaders(request);
            if (Boolean.FALSE.equals(traceData.shouldBeTraced())) {
                serverTracer.setStateNoTracing();
                logger.debug("Not tracing request");
            } else {
                String spanName = getSpanName(traceData, (HttpServletRequest) request);
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
        chain.doFilter(request, response);
        serverTracer.setServerSend();
    }

    private void submitEndpoint(HttpServletRequest request) {
        if (!endPointSubmitter.endPointSubmitted()) {
            String contextPath = request.getContextPath();
            String localAddr = request.getLocalAddr();
            int localPort = request.getLocalPort();
            endPointSubmitter.submit(localAddr, localPort, contextPath);
            logger.debug("Setting endpoint: addr: {}, port: {}, contextpath: {}", localAddr, localPort, contextPath);
        }
    }

    private String getSpanName(TraceData traceData, HttpServletRequest request) {
        if (traceData.getSpanName() == null || traceData.getSpanName().isEmpty()) {
            return request.getRequestURI();
        }
        return traceData.getSpanName();
    }

    private TraceData getTraceDataFromHeaders(ServletRequest request) {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
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
