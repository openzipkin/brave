package com.github.kristofa.brave.resteasy;

import java.net.URL;

import javax.ws.rs.ext.Provider;

import org.apache.commons.lang.Validate;
import org.apache.commons.lang3.StringUtils;
import org.jboss.resteasy.annotations.interception.ClientInterceptor;
import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.spi.interception.ClientExecutionContext;
import org.jboss.resteasy.spi.interception.ClientExecutionInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.SpanId;

/**
 * {@link ClientExecutionInterceptor} that uses the {@link ClientTracer} to set up a new span. </p> It adds the necessary
 * HTTP header parameters to the request to propagate trace information. It also adds some span annotations:
 * <ul>
 * <li>Binary Annotation, key: request, value: http method and full request url.</li>
 * <li>Binary Annoration, key: response.code, value: http reponse code. This annotation is only submitted when response code
 * is unsuccessful</li>
 * <li>Annotation: failure. Only submitted when response code is unsuccessful. This allows us to filter on unsuccessful
 * requests.
 * </ul>
 * If you add a http header with key: X-B3-SpanName, and with a custom span name as value this value will be used as span
 * name iso the path.
 * <p/>
 * We assume the first part of the URI is the context path. The context name will be used as service name in endpoint.
 * Remaining part of path will be used as span name unless X-B3-SpanName http header is set. For example, if we have URI:
 * <p/>
 * <code>http://localhost:8080/service/path/a/b</code>
 * <p/>
 * The service name will be 'service. The span name will be '/path/a/b'.
 * 
 * @author kristof
 */
@Component
@Provider
@ClientInterceptor
public class BraveClientExecutionInterceptor implements ClientExecutionInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(BraveClientExecutionInterceptor.class);
    private static final String REQUEST_ANNOTATION = "request";
    private static final String FAILURE_ANNOTATION = "failure";
    private static final String HTTP_RESPONSE_CODE_ANNOTATION = "http.responsecode";
    private static final String TRUE = "true";
    private static final String FALSE = "false";

    private final ClientTracer clientTracer;

    /**
     * Create a new instance.
     * 
     * @param clientTracer ClientTracer.
     */
    @Autowired
    public BraveClientExecutionInterceptor(final ClientTracer clientTracer) {
        Validate.notNull(clientTracer);
        this.clientTracer = clientTracer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ClientResponse<?> execute(final ClientExecutionContext ctx) throws Exception {

        final ClientRequest request = ctx.getRequest();
        final URL url = new URL(request.getUri());
        final String[] serviceAndSpanName = buildServiceAndSpanName(url);

        String spanName = request.getHeaders().getFirst(BraveHttpHeaders.SpanName.getName());
        if (StringUtils.isEmpty(spanName)) {
            spanName = serviceAndSpanName[1];
        }

        final SpanId newSpanId = clientTracer.startNewSpan(spanName);
        if (newSpanId != null) {
            LOGGER.debug("Will trace request. Span Id returned from ClientTracer: {}", newSpanId);
            request.header(BraveHttpHeaders.Sampled.getName(), TRUE);
            request.header(BraveHttpHeaders.TraceId.getName(), newSpanId.getTraceId());
            request.header(BraveHttpHeaders.SpanId.getName(), newSpanId.getSpanId());
            if (newSpanId.getParentSpanId() != null) {
                request.header(BraveHttpHeaders.ParentSpanId.getName(), newSpanId.getParentSpanId());
            }
            if (serviceAndSpanName[0] != null) {
                clientTracer.setCurrentClientServiceName(serviceAndSpanName[0]);
            }
        } else {
            LOGGER.debug("Will not trace request.");
            request.header(BraveHttpHeaders.Sampled.getName(), FALSE);
        }

        clientTracer.submitBinaryAnnotation(REQUEST_ANNOTATION, request.getHttpMethod() + " " + request.getUri());
        clientTracer.setClientSent();
        try {
            final ClientResponse<?> response = ctx.proceed();
            final int responseStatus = response.getStatus();
            if (responseStatus < 200 || responseStatus > 299) {
                // In this case response will be the error message.
                clientTracer.submitBinaryAnnotation(HTTP_RESPONSE_CODE_ANNOTATION, responseStatus);
                clientTracer.submitAnnotation(FAILURE_ANNOTATION);
            }
            return response;
        } catch (final Exception e) {
            clientTracer.submitAnnotation(FAILURE_ANNOTATION);
            throw e;
        } finally {
            clientTracer.setClientReceived();
        }
    }

    private String[] buildServiceAndSpanName(final URL url) {

        final String[] parts = new String[2];
        final String path = url.getPath();
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
