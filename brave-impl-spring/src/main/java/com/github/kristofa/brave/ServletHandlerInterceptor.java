package com.github.kristofa.brave;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.google.common.base.Optional.fromNullable;

public class ServletHandlerInterceptor extends HandlerInterceptorAdapter {

    static final String HTTP_SERVER_SPAN_ATTRIBUTE = ServletHandlerInterceptor.class.getName() + ".server-span";
    private static final Function<String, Long> TO_HEX = new Function<String, Long>() {
        @Override
        public Long apply(final String s) {
            return IdConversion.convertToLong(s);
        }
    };
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

        final Optional<String> sampled = fromNullable(request.getHeader(BraveHttpHeaders.Sampled.getName()));
        if (!Boolean.valueOf(sampled.or(Boolean.TRUE.toString()))) {
            serverTracer.setStateNoTracing();
            return;
        }

        updateServerState(request);

        serverTracer.setServerReceived();
    }

    private void updateServerState(final HttpServletRequest request) {
        final Optional<Long> traceId = fromNullable(request.getHeader(BraveHttpHeaders.TraceId.getName())).transform(TO_HEX);
        final Optional<Long> spanId = fromNullable(request.getHeader(BraveHttpHeaders.SpanId.getName())).transform(TO_HEX);
        final String spanName = getSpanName(request.getHeader(BraveHttpHeaders.SpanName.getName()), request);

        if (traceId.isPresent() && spanId.isPresent()) {
            final Optional<Long> parentSpanId = fromNullable(request.getHeader(BraveHttpHeaders.ParentSpanId.getName())).transform(TO_HEX);
            serverTracer.setStateCurrentTrace(traceId.get(), spanId.get(), parentSpanId.orNull(), spanName);
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
