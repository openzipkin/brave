package com.github.kristofa.brave.client;

import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.client.spanfilter.SpanNameFilter;
import com.github.kristofa.brave.internal.Nullable;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Spring {@link org.springframework.web.client.RestTemplate RestTemplate} {@link ClientHttpRequestInterceptor} that adds brave/zipkin annotations to outgoing client request and
 * logs the response.
 * <p/>
 * We assume the first part of the URI is the context path. The context name will be used as service name in endpoint.
 * Remaining part of path will be used as span name unless X-B3-SpanName http header is set. For example, if we have URI:
 * <p/>
 * <code>/service/path/a/b</code>
 * <p/>
 * The service name will be 'service'. The span name will be '/path/a/b'.
 * <p/>
 * For the response, it inspects the state. If the response indicates an error it submits error code and failure annotation. Finally it submits the client received annotation.
 */
public class BraveClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    private final ClientRequestInterceptor clientRequestInterceptor;
    private final ClientResponseInterceptor clientResponseInterceptor;
    private final String serviceName;

    /**
     * Creates a new instance.
     *
     * @param clientTracer ClientTracer should not be <code>null</code>.
     * @param serviceName Nullable Service Name override
     * @param spanNameFilter Nullable {@link SpanNameFilter}
     */
    public BraveClientHttpRequestInterceptor(final ClientTracer clientTracer, @Nullable final String serviceName,
                                             @Nullable final SpanNameFilter spanNameFilter) {
        this(new ClientRequestInterceptor( checkNotNull(clientTracer, "Null clientTracer"), spanNameFilter),
                new ClientResponseInterceptor( checkNotNull(clientTracer, "Null clientTracer")), serviceName);
    }

    /**
     * Creates a new instance.
     *
     * @param clientTracer ClientTracer should not be <code>null</code>.
     * @param serviceName Nullable Service Name override
     */
    public BraveClientHttpRequestInterceptor(final ClientTracer clientTracer, @Nullable final String serviceName) {
        this(clientTracer, serviceName, null);
    }

    // VisibleForTesting
    BraveClientHttpRequestInterceptor(final ClientRequestInterceptor clientRequestInterceptor,
                                      final ClientResponseInterceptor clientResponseInterceptor,
                                      final String serviceName) {
        this.clientRequestInterceptor = clientRequestInterceptor;
        this.clientResponseInterceptor = clientResponseInterceptor;
        this.serviceName = serviceName;
    }

    @Override
    public ClientHttpResponse intercept(final HttpRequest request, final byte[] body, final ClientHttpRequestExecution execution) throws IOException {
        clientRequestInterceptor.handle(new SpringRequestAdapter(request), serviceName);

        final ClientHttpResponse response = execution.execute(request, body);

        clientResponseInterceptor.handle(new SpringResponseAdapter(response));
        return response;
    }
}
