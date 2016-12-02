package com.github.kristofa.brave.spring;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.ServerSpan;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.HttpResponse;
import com.github.kristofa.brave.http.HttpServerRequest;
import com.github.kristofa.brave.http.HttpServerRequestAdapter;
import com.github.kristofa.brave.http.HttpServerResponseAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import org.springframework.context.annotation.Configuration;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.URISyntaxException;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

@Configuration
public class ServletHandlerInterceptor extends HandlerInterceptorAdapter {

    static final String HTTP_SERVER_SPAN_ATTRIBUTE = ServletHandlerInterceptor.class.getName() + ".server-span";

    /** Creates a tracing interceptor with defaults. Use {@link #builder(Brave)} to customize. */
    public static ServletHandlerInterceptor create(Brave brave) {
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

        public ServletHandlerInterceptor build() {
            return new ServletHandlerInterceptor(this);
        }
    }

    private final ServerRequestInterceptor requestInterceptor;
    private final ServerResponseInterceptor responseInterceptor;
    private final ServerSpanThreadBinder serverThreadBinder;
    private final SpanNameProvider spanNameProvider;

    @Autowired // internal
    ServletHandlerInterceptor(SpanNameProvider spanNameProvider, Brave brave) {
        this(builder(brave).spanNameProvider(spanNameProvider));
    }

    ServletHandlerInterceptor(Builder b) { // intentionally hidden
        this.requestInterceptor = b.brave.serverRequestInterceptor();
        this.responseInterceptor = b.brave.serverResponseInterceptor();
        this.serverThreadBinder = b.brave.serverSpanThreadBinder();
        this.spanNameProvider = b.spanNameProvider;
    }

    /**
     * @deprecated please use {@link #create(Brave)} or {@link #builder(Brave)}
     */
    @Deprecated
    public ServletHandlerInterceptor(ServerRequestInterceptor requestInterceptor, ServerResponseInterceptor responseInterceptor, SpanNameProvider spanNameProvider, final ServerSpanThreadBinder serverThreadBinder) {
        this.requestInterceptor = requestInterceptor;
        this.spanNameProvider = spanNameProvider;
        this.responseInterceptor = responseInterceptor;
        this.serverThreadBinder = serverThreadBinder;
    }

    @Override
    public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler) {
        requestInterceptor.handle(new HttpServerRequestAdapter(new HttpServerRequest() {
            @Override
            public String getHttpHeaderValue(String headerName) {
                return request.getHeader(headerName);
            }

            @Override
            public URI getUri() {
                try {
                    return new URI(request.getRequestURI());
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public String getHttpMethod() {
                return request.getMethod();
            }
        }, spanNameProvider));

        return true;
    }

    @Override
    public void afterConcurrentHandlingStarted(final HttpServletRequest request, final HttpServletResponse response, final Object handler) {
        request.setAttribute(HTTP_SERVER_SPAN_ATTRIBUTE, serverThreadBinder.getCurrentServerSpan());
        serverThreadBinder.setCurrentSpan(null);
    }

    @Override
    public void afterCompletion(final HttpServletRequest request, final HttpServletResponse response, final Object handler, final Exception ex) {

        final ServerSpan span = (ServerSpan) request.getAttribute(HTTP_SERVER_SPAN_ATTRIBUTE);

        if (span != null) {
            serverThreadBinder.setCurrentSpan(span);
        }

       responseInterceptor.handle(new HttpServerResponseAdapter(response::getStatus));
    }

}
