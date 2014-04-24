package com.github.kristofa.brave.httpclient;

import java.io.IOException;

import org.apache.commons.lang.Validate;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.SpanId;

/**
 * Apache HttpClient {@link HttpRequestInterceptor} that adds brave/zipkin annotations to outgoing client request.
 * 
 * @author kristof
 */
public class BraveHttpRequestInterceptor implements HttpRequestInterceptor {

    private final static Logger LOGGER = LoggerFactory.getLogger(BraveHttpRequestInterceptor.class);
    private static final String REQUEST_ANNOTATION = "request";
    private static final String TRUE = "true";
    private static final String FALSE = "false";

    private final ClientTracer clientTracer;

    /**
     * Creates a new instance.
     * 
     * @param clientTracer ClientTracer should not be <code>null</code>.
     */
    public BraveHttpRequestInterceptor(final ClientTracer clientTracer) {
        Validate.notNull(clientTracer);
        this.clientTracer = clientTracer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {

        String spanName = null;
        final Header spanNameHeader = request.getFirstHeader(BraveHttpHeaders.SpanName.getName());
        if (spanNameHeader != null) {
            spanName = spanNameHeader.getValue();
        }

        if (StringUtils.isEmpty(spanName)) {
            spanName = request.getRequestLine().getUri();
        }

        final SpanId newSpanId = clientTracer.startNewSpan(spanName);
        if (newSpanId != null) {
            LOGGER.debug("Will trace request. Span Id returned from ClientTracer: {}", newSpanId);
            request.addHeader(BraveHttpHeaders.Sampled.getName(), TRUE);
            request.addHeader(BraveHttpHeaders.TraceId.getName(), String.valueOf(newSpanId.getTraceId()));
            request.addHeader(BraveHttpHeaders.SpanId.getName(), String.valueOf(newSpanId.getSpanId()));
            if (newSpanId.getParentSpanId() != null) {
                request.addHeader(BraveHttpHeaders.ParentSpanId.getName(), String.valueOf(newSpanId.getParentSpanId()));
            }
        } else {
            LOGGER.debug("Will not trace request.");
            request.addHeader(BraveHttpHeaders.Sampled.getName(), FALSE);
        }

        clientTracer.submitBinaryAnnotation(REQUEST_ANNOTATION, request.getRequestLine().getMethod() + " "
            + request.getRequestLine().getUri());
        clientTracer.setClientSent();

    }

}
