package com.github.kristofa.brave.httpclient;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.lang.Validate;
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
 * <p/>
 * We assume the first part of the URI is the context path. The context name will be used as service name in endpoint.
 * Remaining part of path will be used as span name unless X-B3-SpanName http header is set. For example, if we have URI:
 * <p/>
 * <code>/service/path/a/b</code>
 * <p/>
 * The service name will be 'service'. The span name will be '/path/a/b'.
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

        String[] serviceAndSpanNames;
        try {
            serviceAndSpanNames = buildServiceAndSpanName(new URI(request.getRequestLine().getUri()));
        } catch (final URISyntaxException e) {
            throw new HttpException(e.getMessage(), e);
        }

        String spanName = null;
        final Header spanNameHeader = request.getFirstHeader(BraveHttpHeaders.SpanName.getName());
        if (spanNameHeader != null) {
            spanName = spanNameHeader.getValue();
        } else {
            spanName = serviceAndSpanNames[1];
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
            if (serviceAndSpanNames[0] != null) {
                clientTracer.setCurrentClientServiceName(serviceAndSpanNames[0]);
            }
        } else {
            LOGGER.debug("Will not trace request.");
            request.addHeader(BraveHttpHeaders.Sampled.getName(), FALSE);
        }

        clientTracer.submitBinaryAnnotation(REQUEST_ANNOTATION, request.getRequestLine().getMethod() + " "
            + request.getRequestLine().getUri());
        clientTracer.setClientSent();

    }

    private String[] buildServiceAndSpanName(final URI uri) {

        final String path = uri.getPath();

        final String[] parts = new String[2];
        final String[] split = path.split("/");
        if (split.length > 2 && path.startsWith("/")) {
            // Path starts with / and first part till 2nd / is context path. Left over is path for service.
            final int contextPathSeparatorIndex = path.indexOf("/", 1);
            parts[0] = path.substring(1, contextPathSeparatorIndex);
            parts[1] = path.substring(contextPathSeparatorIndex);
        } else {
            parts[1] = path;
        }
        return parts;
    }

}
