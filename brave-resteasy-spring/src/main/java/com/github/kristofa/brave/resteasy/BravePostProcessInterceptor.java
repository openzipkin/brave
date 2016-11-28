package com.github.kristofa.brave.resteasy;

import javax.ws.rs.ext.Provider;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerResponseAdapter;
import com.github.kristofa.brave.TagExtractor;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.http.HttpResponse;
import com.github.kristofa.brave.http.HttpServerResponseAdapter;
import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.interception.PostProcessInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Rest Easy {@link PostProcessInterceptor} that will submit server send state.
 * 
 * @author kristof
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

    public static final class Builder implements TagExtractor.Config<Builder> {
        final Brave brave;
        final HttpServerResponseAdapter.FactoryBuilder adapterFactoryBuilder
            = HttpServerResponseAdapter.factoryBuilder();

        Builder(Brave brave) { // intentionally hidden
            this.brave = checkNotNull(brave, "brave");
        }

        @Override public Builder addKey(String key) {
            adapterFactoryBuilder.addKey(key);
            return this;
        }

        @Override
        public Builder addValueParserFactory(TagExtractor.ValueParserFactory factory) {
            adapterFactoryBuilder.addValueParserFactory(factory);
            return this;
        }

        public BravePostProcessInterceptor build() {
            return new BravePostProcessInterceptor(this);
        }
    }

    private final ServerResponseInterceptor interceptor;
    private final ServerResponseAdapter.Factory<HttpResponse> adapterFactory;

    @Autowired // internal
    BravePostProcessInterceptor(Brave brave) {
        this(builder(brave));
    }

    BravePostProcessInterceptor(Builder b) { // intentionally hidden
        this.interceptor = b.brave.serverResponseInterceptor();
        this.adapterFactory = b.adapterFactoryBuilder.build(HttpResponse.class);
    }

    /**
     * @deprecated please use {@link #create(Brave)} or {@link #builder(Brave)}
     */
    @Deprecated
    public BravePostProcessInterceptor(ServerResponseInterceptor interceptor) {
        this.interceptor = interceptor;
        this.adapterFactory = HttpServerResponseAdapter.factoryBuilder()
            .build(HttpResponse.class);
    }

    @Override
    public void postProcess(final ServerResponse response) {

        HttpResponse httpResponse = new HttpResponse() {

            @Override
            public int getHttpStatusCode() {
                return response.getStatus();
            }
        };
        ServerResponseAdapter adapter = adapterFactory.create(httpResponse);
        interceptor.handle(adapter);
    }

}
