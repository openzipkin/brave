package com.github.kristofa.brave.cxf;

import com.github.kristofa.brave.*;
import com.github.kristofa.brave.client.ClientResponseInterceptor;
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

    private final ZipkinConfig zipkinConfig;

    private final ClientResponseInterceptor traceResponseBuilder;

    public InZipkinInterceptor(final ZipkinConfig zipkinConfig) {
        super(Phase.RECEIVE);
        Validate.notNull(zipkinConfig);
        this.zipkinConfig = zipkinConfig; //Can be null in isRoot mode
        this.traceResponseBuilder = new ClientResponseInterceptor(zipkinConfig.getClientTracer());
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        ServerTracer serverTracer = zipkinConfig.getServerTracer();
        AttemptLimiter attemptLimiter = zipkinConfig.getInAttemptLimiter();

        if (!attemptLimiter.shouldTry())
            return;
        try {

            if (isRequestor(message)) {
                traceResponseBuilder.handle(new CXFClientResponseAdapter(message));
                attemptLimiter.reportSuccess();
                return;
            }

            //IsRoot mode, no incoming request tracing
            if (zipkinConfig.IsRoot())
                return;

            HttpServletRequest request = (HttpServletRequest) message.get(AbstractHTTPDestination.HTTP_REQUEST);

            if (request == null) {
                attemptLimiter.reportFailure();
                return;
            }

            SpanAddress spanAddress = new SpanAddress(request, zipkinConfig.getServiceName());

            submitEndpoint(spanAddress);
            serverTracer.clearCurrentSpan();

            final ZipkinTraceData traceData = getTraceData(request);

            if (Boolean.FALSE.equals(traceData.shouldBeTraced())) {
                serverTracer.setStateNoTracing();
                LOG.debug("Received indication that we should NOT trace.");
            } else {
                final String spanName = getSpanName(spanAddress, traceData);
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
            attemptLimiter.reportSuccess();
        } catch (Exception e) {
            LOG.error("Zipkin interception exception", e);
            attemptLimiter.reportFailure();
        }
    }

    private String getSpanName(final SpanAddress address, final ZipkinTraceData traceData) throws WebApplicationException {
        if (StringUtils.isNotBlank(traceData.getSpanName())) {
            return traceData.getSpanName();
        } else {
            return address.getSpanName();
        }
    }

    private void submitEndpoint(SpanAddress address) {
        EndPointSubmitter endPointSubmitter = zipkinConfig.getEndPointSubmitter();
        if (!endPointSubmitter.endPointSubmitted()) {
            LOG.debug("Setting endpoint: addr: {}, port: {}, servicename: {}",
                    address.getLocalIPv4(), address.getLocalPort(), address.getServiceName());
            endPointSubmitter.submit(address.getLocalIPv4(), address.getLocalPort(), address.getServiceName());
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
        return firstStringValueFor == null ? null : IdConversion.convertToLong(firstStringValueFor);

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