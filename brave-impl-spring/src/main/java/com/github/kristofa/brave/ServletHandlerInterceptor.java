package com.github.kristofa.brave;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.github.kristofa.brave.IdConversion.convertToLong;

public class ServletHandlerInterceptor extends HandlerInterceptorAdapter {

    static final String HTTP_SERVER_SPAN_ATTRIBUTE = ServletHandlerInterceptor.class.getName() + ".server-span";
    private final ServerSpanThreadBinder serverThreadBinder;
    private final ServerTracer serverTracer;
    private final EndPointSubmitter endPointSubmitter;

    @Autowired
    public ServletHandlerInterceptor(final ServerTracer serverTracer, final ServerSpanThreadBinder serverThreadBinder, final EndPointSubmitter endPointSubmitter) {
        this.serverTracer = serverTracer;
        this.serverThreadBinder = serverThreadBinder;
        this.endPointSubmitter = endPointSubmitter;
    }

    @Override
    public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler) {
        beginTrace(request);

        return true;
    }

    @Override
    public void afterConcurrentHandlingStarted(final HttpServletRequest request, final HttpServletResponse response, final Object handler) {
        request.setAttribute(HTTP_SERVER_SPAN_ATTRIBUTE, serverThreadBinder.getCurrentServerSpan());
        serverTracer.clearCurrentSpan();
    }

    @Override
    public void afterCompletion(final HttpServletRequest request, final HttpServletResponse response, final Object handler, final Exception ex) {

        final ServerSpan span = (ServerSpan) request.getAttribute(HTTP_SERVER_SPAN_ATTRIBUTE);

        if (span != null) {
            serverThreadBinder.setCurrentSpan(span);
        }

        try {
            serverTracer.setServerSend();
        } finally {
            serverTracer.clearCurrentSpan();
        }
    }

    protected String getServiceName(final HttpServletRequest request) {
        return request.getContextPath();
    }

    private void beginTrace(final HttpServletRequest request) {
        if (!endPointSubmitter.endPointSubmitted()) {
            endPointSubmitter.submit(request.getLocalAddr(), request.getLocalPort(), getServiceName(request));
        }

        final String sampled = request.getHeader(BraveHttpHeaders.Sampled.getName());
        if (!Boolean.valueOf(sampled != null ? sampled : Boolean.TRUE.toString())) {
            serverTracer.setStateNoTracing();
            return;
        }

        updateServerState(request);

        serverTracer.setServerReceived();
    }

    private void updateServerState(final HttpServletRequest request) {
        final String traceId = request.getHeader(BraveHttpHeaders.TraceId.getName());
        final String spanId = request.getHeader(BraveHttpHeaders.SpanId.getName());
        final String spanName = getSpanName(request.getHeader(BraveHttpHeaders.SpanName.getName()), request);

        if (traceId != null && spanId != null) {
            final String parentSpanId = request.getHeader(BraveHttpHeaders.ParentSpanId.getName());
            serverTracer.setStateCurrentTrace(convertToLong(traceId), convertToLong(spanId),
                                              parentSpanId != null ? convertToLong(parentSpanId) : null, spanName);
        } else {
            serverTracer.setStateUnknown(spanName);
        }
    }

    private String getSpanName(final String name, final HttpServletRequest request) {
        if (StringUtils.isEmpty(name)) {
            return request.getRequestURI();
        }
        return name;
    }

}
