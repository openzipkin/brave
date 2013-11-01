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
 * name iso the path name.
 * 
 * @author kristof
 */
@Component
@Provider
@ClientInterceptor
public class BraveClientExecutionInterceptor implements ClientExecutionInterceptor {

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
        String spanName = request.getHeaders().getFirst(BraveHttpHeaders.SpanName.getName());

        if (StringUtils.isEmpty(spanName)) {
            final URL url = new URL(request.getUri());
            spanName = url.getPath();
        }

        final SpanId newSpanId = clientTracer.startNewSpan(spanName);
        if (newSpanId != null) {
            request.header(BraveHttpHeaders.Sampled.getName(), TRUE);
            request.header(BraveHttpHeaders.TraceId.getName(), newSpanId.getTraceId());
            request.header(BraveHttpHeaders.SpanId.getName(), newSpanId.getSpanId());
            if (newSpanId.getParentSpanId() != null) {
                request.header(BraveHttpHeaders.ParentSpanId.getName(), newSpanId.getParentSpanId());
            }
        } else {
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
}
