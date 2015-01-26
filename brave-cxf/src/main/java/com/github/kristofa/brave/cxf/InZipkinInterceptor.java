package com.github.kristofa.brave.cxf;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.EndPointSubmitter;
import com.github.kristofa.brave.ServerTracer;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * User: fedor
 * Date: 30.12.2014
 * Time: 17:04
 */
public class InZipkinInterceptor extends AbstractPhaseInterceptor<Message> {
    private static final Logger LOG = LoggerFactory.getLogger(InZipkinInterceptor.class);

    private final EndPointSubmitter endPointSubmitter;
    private final ServerTracer serverTracer;

    public InZipkinInterceptor(final EndPointSubmitter endPointSubmitter, final ServerTracer serverTracer) {
        super(Phase.RECEIVE);
        Validate.notNull(endPointSubmitter);
        Validate.notNull(serverTracer);
        this.endPointSubmitter = endPointSubmitter;
        this.serverTracer = serverTracer;
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        HttpServletRequest request = (HttpServletRequest) message.get(AbstractHTTPDestination.HTTP_REQUEST);

        if (request == null)
            return;

        submitEndpoint(request);
        serverTracer.clearCurrentSpan();

        final ZipkinTraceData traceData = getTraceData(request);

        if (Boolean.FALSE.equals(traceData.shouldBeTraced())) {
            serverTracer.setStateNoTracing();
            LOG.debug("Received indication that we should NOT trace.");
        } else {
            final String spanName = getSpanName(message, traceData);
            if (traceData.getTraceId() != null && traceData.getSpanId() != null) {

                LOG.debug("Received span information as part of request.");
                serverTracer.setStateCurrentTrace(traceData.getTraceId(), traceData.getSpanId(),
                        traceData.getParentSpanId(), spanName);
            } else {
                LOG.debug("Received no span state.");
                serverTracer.setStateUnknown(spanName);
            }
            serverTracer.setServerReceived();
        }
    }

    private String getSpanName(final Message message, final ZipkinTraceData traceData) throws WebApplicationException {
        if (StringUtils.isNotBlank(traceData.getSpanName())) {
            return traceData.getSpanName();
        } else {
            //TODO: Fix span name creation, method name is preferred
            return message.get("org.apache.cxf.request.uri").toString();//   request.getPreprocessedPath();
        }
    }

    private void submitEndpoint(HttpServletRequest servletRequest) {
        if (!endPointSubmitter.endPointSubmitted()) {
            final String localAddr = servletRequest.getLocalAddr();
            //TODO: Fix IPv6 and host names instead of IP
            final int localPort = servletRequest.getLocalPort();
            final String serviceName = getServiceName(servletRequest);
            LOG.debug("Setting endpoint: addr: {}, port: {}, servicename: {}", localAddr, localPort, serviceName);
            endPointSubmitter.submit(localAddr, localPort, serviceName);
        }
    }

    private String getServiceName(HttpServletRequest servletRequest) {
        //TODO: Extract service name without /api and method name
        String serviceName = servletRequest.getContextPath();
        if (StringUtils.isBlank(serviceName))
            serviceName = servletRequest.getServletPath();
        if (serviceName.startsWith("/")) {
            return serviceName.substring(1);
        } else {
            return serviceName;
        }
    }

    private ZipkinTraceData getTraceData(final HttpServletRequest request) {
        final ZipkinTraceData traceData = new ZipkinTraceData();

        List<String> headersList = Collections.list(request.getHeaderNames());

        for (final String headerKey : headersList) {
            Enumeration<String> headers = request.getHeaders(headerKey);

            LOG.debug("{}={}", headerKey, getFirstStringValueFor(request.getHeaders(headerKey)));
            if (BraveHttpHeaders.TraceId.getName().equalsIgnoreCase(headerKey)) {
                traceData.setTraceId(getFirstLongValueFor(headers));
            } else if (BraveHttpHeaders.SpanId.getName().equalsIgnoreCase(headerKey)) {
                traceData.setSpanId(getFirstLongValueFor(headers));
            } else if (BraveHttpHeaders.ParentSpanId.getName().equalsIgnoreCase(headerKey)) {
                traceData.setParentSpanId(getFirstLongValueFor(headers));
            } else if (BraveHttpHeaders.Sampled.getName().equalsIgnoreCase(headerKey)) {
                traceData.setShouldBeSampled(getFirstBooleanValueFor(headers));
            } else if (BraveHttpHeaders.SpanName.getName().equalsIgnoreCase(headerKey)) {
                traceData.setSpanName(getFirstStringValueFor(headers));
            }
        }
        return traceData;
    }

    private Long getFirstLongValueFor(final Enumeration<String> headers) {
        final String firstStringValueFor = getFirstStringValueFor(headers);
        return firstStringValueFor == null ? null : Long.valueOf(firstStringValueFor, 16);

    }

    private Boolean getFirstBooleanValueFor(final Enumeration<String> headers) {
        final String firstStringValueFor = getFirstStringValueFor(headers);
        return firstStringValueFor == null ? null : Boolean.valueOf(firstStringValueFor);
    }

    private String getFirstStringValueFor(final Enumeration<String> headers) {
        if (!headers.hasMoreElements())
            return null;
        return headers.nextElement();
     }
}