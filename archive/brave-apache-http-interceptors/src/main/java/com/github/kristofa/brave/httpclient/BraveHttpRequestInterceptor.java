package com.github.kristofa.brave.httpclient;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientSpanThreadBinder;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Apache http client request interceptor.
 *
 * @deprecated Replaced by {@code TracingHttpClientBuilder} from brave-instrumentation-httpclient
 */
@Deprecated
public class BraveHttpRequestInterceptor implements HttpRequestInterceptor {

    static final String SPAN_ATTRIBUTE = BraveHttpResponseInterceptor.class.getName() + ".span";

    /** Creates a tracing interceptor with defaults. Use {@link #builder(Brave)} to customize. */
    public static BraveHttpRequestInterceptor create(Brave brave) {
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

        public BraveHttpRequestInterceptor build() {
            return new BraveHttpRequestInterceptor(this);
        }
    }

    private final ClientRequestInterceptor requestInterceptor;
    private final SpanNameProvider spanNameProvider;
    private final ClientSpanThreadBinder spanThreadBinder;

    BraveHttpRequestInterceptor(Builder b) { // intentionally hidden
        this.requestInterceptor = b.brave.clientRequestInterceptor();
        this.spanNameProvider = b.spanNameProvider;
        this.spanThreadBinder = b.brave.clientSpanThreadBinder();
    }

    /**
     * Creates a new instance.
     *
     * @param requestInterceptor
     * @param spanNameProvider Provides span name for request.
     * @deprecated please use {@link #create(Brave)} or {@link #builder(Brave)}; using this constructor means that
     * a "client received" event will not be sent when using {@code HttpAsyncClient}
     */
    @Deprecated
    public BraveHttpRequestInterceptor(ClientRequestInterceptor requestInterceptor, SpanNameProvider spanNameProvider) {
        this.requestInterceptor = requestInterceptor;
        this.spanNameProvider = spanNameProvider;
        this.spanThreadBinder = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(final HttpRequest request, final HttpContext context) {
        HttpClientRequestAdapter adapter = new HttpClientRequestAdapter(new HttpClientRequestImpl(request), spanNameProvider);
        requestInterceptor.handle(adapter);
        if (spanThreadBinder != null) {
            // When using HttpAsyncClient, the response interceptor is not run on the same thread, so for it the client
            // span would just not be set. By putting it into the HttpContext, we can keep track of the client span and
            // retrieve it later in the response interceptor.
            context.setAttribute(SPAN_ATTRIBUTE, spanThreadBinder.getCurrentClientSpan());
        }
    }
}
