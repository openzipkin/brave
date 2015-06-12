package com.github.kristofa.brave.resteasy;

import javax.annotation.Nullable;
import javax.ws.rs.ext.Provider;

import org.apache.commons.lang3.Validate;
import org.jboss.resteasy.annotations.interception.ClientInterceptor;
import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.spi.interception.ClientExecutionContext;
import org.jboss.resteasy.spi.interception.ClientExecutionInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.client.ClientRequestInterceptor;
import com.github.kristofa.brave.client.ClientResponseInterceptor;
import com.github.kristofa.brave.client.spanfilter.SpanNameFilter;

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

    private final ClientRequestInterceptor clientRequestInterceptor;
    private final ClientResponseInterceptor clientResponseInterceptor;

    /**
     * Create a new instance.
     *
     * @param clientTracer ClientTracer.
     */
    @Autowired(required = false)
    public BraveClientExecutionInterceptor(final ClientTracer clientTracer) {
        this(clientTracer, null);
    }

    /**
     * Create a new instance.
     *
     * @param clientTracer ClientTracer.
     * @param spanNameFilter Nullable {@link SpanNameFilter}
     */
    @Autowired(required = false)
    public BraveClientExecutionInterceptor(final ClientTracer clientTracer, @Nullable final SpanNameFilter spanNameFilter) {
        Validate.notNull(clientTracer);
        clientRequestInterceptor = new ClientRequestInterceptor(clientTracer, spanNameFilter);
        clientResponseInterceptor = new ClientResponseInterceptor(clientTracer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ClientResponse<?> execute(final ClientExecutionContext ctx) throws Exception {

        final ClientRequest request = ctx.getRequest();

        clientRequestInterceptor.handle(new RestEasyClientRequestAdapter(request), null);

        ClientResponse<?> response = null;
        Exception exception = null;
        try {
            response = ctx.proceed();
        } catch (final Exception e) {
            exception = e;
        }

        clientResponseInterceptor.handle(new RestEasyClientResponseAdapter(response));
        if (exception != null) {
            throw exception;
        }
        return response;
    }
}
