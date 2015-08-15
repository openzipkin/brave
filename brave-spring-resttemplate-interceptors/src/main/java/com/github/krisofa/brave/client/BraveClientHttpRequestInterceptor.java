package com.github.krisofa.brave.client;

import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.NoAnnotationsClientResponseAdapter;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.HttpClientResponseAdapter;
import com.github.kristofa.brave.http.ServiceNameProvider;
import com.github.kristofa.brave.http.SpanNameProvider;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

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

    private final ClientRequestInterceptor requestInterceptor;
    private final ClientResponseInterceptor responseInterceptor;
    private final ServiceNameProvider serviceNameProvider;
    private final SpanNameProvider spanNameProvider;

    /**
     * Creates a new instance.
     *
     * @param serviceNameProvider Provides service name.
     * @param spanNameProvider Provides span name.
     * @param requestInterceptor Client request interceptor.
     * @param responseInterceptor Client response interceptor.
     */
    public BraveClientHttpRequestInterceptor(final ClientRequestInterceptor requestInterceptor, final ClientResponseInterceptor responseInterceptor,
                                             final ServiceNameProvider serviceNameProvider, final SpanNameProvider spanNameProvider) {
        this.requestInterceptor = requestInterceptor;
        this.responseInterceptor = responseInterceptor;
        this.serviceNameProvider = serviceNameProvider;
        this.spanNameProvider = spanNameProvider;
    }

    @Override
    public ClientHttpResponse intercept(final HttpRequest request, final byte[] body, final ClientHttpRequestExecution execution) throws IOException {

        requestInterceptor.handle(new HttpClientRequestAdapter(new SpringHttpClientRequest(request), serviceNameProvider, spanNameProvider));

        final ClientHttpResponse response;

        try {
            response = execution.execute(request, body);
        } catch (RuntimeException | IOException up) {
            // Something went serious wrong communicating with the server; let the exception blow up
            responseInterceptor.handle(NoAnnotationsClientResponseAdapter.getInstance());

            throw up;
        }

        try {
            responseInterceptor.handle(new HttpClientResponseAdapter(new SpringHttpResponse(response.getRawStatusCode())));
        } catch (RuntimeException | IOException up) {
            // Ignore the failure of not being able to get the status code from the response; let the calling code find out themselves
            responseInterceptor.handle(NoAnnotationsClientResponseAdapter.getInstance());
        }

        return response;
    }
}
