package com.github.kristofa.brave.resteasy;

import javax.ws.rs.ext.Provider;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientRequestAdapter;
import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientResponseAdapter;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.NoAnnotationsClientResponseAdapter;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.HttpClientRequest;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.HttpClientResponseAdapter;
import com.github.kristofa.brave.http.HttpResponse;
import com.github.kristofa.brave.http.SpanNameProvider;

import org.jboss.resteasy.annotations.interception.ClientInterceptor;
import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.spi.interception.ClientExecutionContext;
import org.jboss.resteasy.spi.interception.ClientExecutionInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

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

    /** Creates a tracing interceptor with defaults. Use {@link #builder(Brave)} to customize. */
    public static BraveClientExecutionInterceptor create(Brave brave) {
        return new Builder(brave).build();
    }

    public static Builder builder(Brave brave) {
        return new Builder(brave);
    }

    public static final class Builder {
        final Brave brave;
        SpanNameProvider spanNameProvider = new DefaultSpanNameProvider();

        Builder(Brave brave) { // intentionally hidden
            this.brave = checkNotNull(brave, "brave");
        }

        public Builder spanNameProvider(SpanNameProvider spanNameProvider) {
            this.spanNameProvider = checkNotNull(spanNameProvider, "spanNameProvider");
            return this;
        }

        public BraveClientExecutionInterceptor build() {
            return new BraveClientExecutionInterceptor(this);
        }
    }

    private final ClientRequestInterceptor requestInterceptor;
    private final ClientResponseInterceptor responseInterceptor;
    private final SpanNameProvider spanNameProvider;

    @Autowired // internal
    BraveClientExecutionInterceptor(SpanNameProvider spanNameProvider, Brave brave) {
        this(builder(brave).spanNameProvider(spanNameProvider));
    }

    BraveClientExecutionInterceptor(Builder b) { // intentionally hidden
        this.requestInterceptor = b.brave.clientRequestInterceptor();
        this.responseInterceptor = b.brave.clientResponseInterceptor();
        this.spanNameProvider = b.spanNameProvider;
    }

    /**
     * Create a new instance.
     *
     * @param spanNameProvider Provides span name.
     * @param requestInterceptor Client request interceptor.
     * @param responseInterceptor Client response interceptor.
     * @deprecated please use {@link #create(Brave)} or {@link #builder(Brave)}
     */
    @Deprecated
    public BraveClientExecutionInterceptor(SpanNameProvider spanNameProvider, ClientRequestInterceptor requestInterceptor, ClientResponseInterceptor responseInterceptor) {
        this.requestInterceptor = requestInterceptor;
        this.spanNameProvider = spanNameProvider;
        this.responseInterceptor = responseInterceptor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ClientResponse<?> execute(final ClientExecutionContext ctx) throws Exception {

        final ClientRequest request = ctx.getRequest();

        final HttpClientRequest httpClientRequest = new RestEasyHttpClientRequest(request);
        final ClientRequestAdapter adapter = new HttpClientRequestAdapter(httpClientRequest, spanNameProvider);
        requestInterceptor.handle(adapter);

        ClientResponse<?> response = null;
        try {
            response = ctx.proceed();
        } catch (final Exception e) {
            throw e;
        }
        finally
        {
            if (response != null) {
                final HttpResponse httpResponse = new RestEasyHttpClientResponse(response);
                final ClientResponseAdapter responseAdapter = new HttpClientResponseAdapter(httpResponse);
                responseInterceptor.handle(responseAdapter);
            }
            else
            {
                responseInterceptor.handle(NoAnnotationsClientResponseAdapter.getInstance());
            }
        }
        return response;
    }
}
