package com.github.kristofa.brave.resteasy;

import javax.ws.rs.ext.Provider;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.http.HttpResponse;
import com.github.kristofa.brave.http.HttpServerResponseAdapter;
import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.interception.PostProcessInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.kristofa.brave.ServerTracer;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Rest Easy {@link PostProcessInterceptor} that will submit server send state.
 * 
 * @author kristof
 * @deprecated There is no plan to continue supporting RestEasy 2.x
 */
@Component
@Provider
@ServerInterceptor
public class BravePostProcessInterceptor implements PostProcessInterceptor {

    /** Creates a tracing interceptor with defaults. Use {@link #builder(Brave)} to customize. */
    public static BravePostProcessInterceptor create(Brave brave) {
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

        public BravePostProcessInterceptor build() {
            return new BravePostProcessInterceptor(this);
        }
    }

    private final ServerResponseInterceptor responseInterceptor;

    @Autowired // internal
    BravePostProcessInterceptor(Brave brave) {
        this(builder(brave));
    }

    BravePostProcessInterceptor(Builder b) { // intentionally hidden
        this.responseInterceptor = b.brave.serverResponseInterceptor();
    }

    /**
     * Creates a new instance.
     * 
     * @param responseInterceptor {@link ServerTracer}. Should not be null.
     * @deprecated please use {@link #create(Brave)} or {@link #builder(Brave)}
     */
    @Deprecated
    public BravePostProcessInterceptor(ServerResponseInterceptor responseInterceptor) {
        this.responseInterceptor = responseInterceptor;
    }

    @Override
    public void postProcess(final ServerResponse response) {
        HttpResponse httpResponse = response::getStatus;
        HttpServerResponseAdapter adapter = new HttpServerResponseAdapter(httpResponse);
        responseInterceptor.handle(adapter);
    }

}
