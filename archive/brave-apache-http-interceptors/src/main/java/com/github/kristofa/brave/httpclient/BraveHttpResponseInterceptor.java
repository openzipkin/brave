package com.github.kristofa.brave.httpclient;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.ClientSpanThreadBinder;
import com.github.kristofa.brave.http.HttpClientResponseAdapter;
import com.twitter.zipkin.gen.Span;
import java.io.IOException;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.protocol.HttpContext;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Apache http client response interceptor.
 *
 * @deprecated Replaced by {@code TracingHttpClientBuilder} from brave-instrumentation-httpclient
 */
@Deprecated
public class BraveHttpResponseInterceptor implements HttpResponseInterceptor {

    /** Creates a tracing interceptor with defaults. Use {@link #builder(Brave)} to customize. */
    public static BraveHttpResponseInterceptor create(Brave brave) {
        return new Builder(brave).build();
    }

    public static Builder builder(Brave brave) {
        return new Builder(brave);
    }

    public static final class Builder {
        final Brave brave;

        Builder(Brave brave) { // intentionally hidden
           this.brave = checkNotNull(brave, "brave");
        }

        public BraveHttpResponseInterceptor build() {
            return new BraveHttpResponseInterceptor(this);
        }
    }

    private final ClientResponseInterceptor responseInterceptor;
    private final ClientSpanThreadBinder spanThreadBinder;

    BraveHttpResponseInterceptor(Builder b) { // intentionally hidden
        this.responseInterceptor = b.brave.clientResponseInterceptor();
        this.spanThreadBinder = b.brave.clientSpanThreadBinder();
    }

    /**
     * @deprecated please use {@link #create(Brave)} or {@link #builder(Brave)}; using this constructor means that
     * a "client received" event will not be sent when using {@code HttpAsyncClient}
     */
    @Deprecated
    public BraveHttpResponseInterceptor(final ClientResponseInterceptor responseInterceptor) {
        this.responseInterceptor = responseInterceptor;
        this.spanThreadBinder = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(final HttpResponse response, final HttpContext context) throws HttpException, IOException {
        // When using HttpAsyncClient, this interceptor does not run on the same thread as the request interceptor.
        // So in that case, the current client span will be null. To still be able to submit "client received", we get
        // the span from the HttpContext (where we put it in the request interceptor).
        if (spanThreadBinder.getCurrentClientSpan() == null) {
            Object span = context.getAttribute(BraveHttpRequestInterceptor.SPAN_ATTRIBUTE);
            if (span instanceof Span) {
                spanThreadBinder.setCurrentSpan((Span) span);
            }
        }

        final HttpClientResponseImpl httpClientResponse = new HttpClientResponseImpl(response);
        responseInterceptor.handle(new HttpClientResponseAdapter(httpClientResponse));
    }

}
