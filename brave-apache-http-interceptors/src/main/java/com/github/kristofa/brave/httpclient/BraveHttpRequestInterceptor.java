package com.github.kristofa.brave.httpclient;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.github.kristofa.brave.internal.Nullable;
import com.github.kristofa.brave.Propagation;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Apache http client request interceptor.
 */
public class BraveHttpRequestInterceptor implements HttpRequestInterceptor {

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

    @Nullable // nullable while deprecated constructor is in use
    private final Propagation.Injector<HttpRequest> injector;
    private final ClientRequestInterceptor interceptor;
    private final SpanNameProvider spanNameProvider;

    BraveHttpRequestInterceptor(Builder b) { // intentionally hidden
        this.injector = b.brave.propagation().injector(HttpMessage::setHeader);
        this.interceptor = b.brave.clientRequestInterceptor();
        this.spanNameProvider = b.spanNameProvider;
    }

    /**
     * Creates a new instance.
     *
     * @param interceptor
     * @param spanNameProvider Provides span name for request.
     * @deprecated please use {@link #create(Brave)} or {@link #builder(Brave)}
     */
    @Deprecated
    public BraveHttpRequestInterceptor(ClientRequestInterceptor interceptor, SpanNameProvider spanNameProvider) {
        this.injector = null;
        this.interceptor = interceptor;
        this.spanNameProvider = spanNameProvider;
    }

    @Override
    public void process(final HttpRequest request, final HttpContext context) {
        HttpClientRequestAdapter adapter = new HttpClientRequestAdapter(new HttpClientRequestImpl(request), spanNameProvider);
        SpanId spanId = interceptor.internalStartSpan(adapter);
        if (injector != null) {
            injector.injectSpanId(spanId, request);
        } else {
            adapter.addSpanIdToRequest(spanId);
        }
    }
}
