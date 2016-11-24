package com.github.kristofa.brave.httpclient;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.http.HttpClientResponseAdapter;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Apache http client response interceptor.
 */
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

    BraveHttpResponseInterceptor(Builder b) { // intentionally hidden
        this.responseInterceptor = b.brave.clientResponseInterceptor();
    }

    /**
     * @deprecated please use {@link #create(Brave)} or {@link #builder(Brave)}
     */
    @Deprecated
    public BraveHttpResponseInterceptor(final ClientResponseInterceptor responseInterceptor) {
        this.responseInterceptor = responseInterceptor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(final HttpResponse response, final HttpContext context) throws HttpException, IOException {
        final HttpClientResponseImpl httpClientResponse = new HttpClientResponseImpl(response);
        responseInterceptor.handle(new HttpClientResponseAdapter(httpClientResponse));
    }

}
