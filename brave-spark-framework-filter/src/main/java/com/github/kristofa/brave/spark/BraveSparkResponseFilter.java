package com.github.kristofa.brave.spark;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.HttpServerResponseAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import spark.Filter;
import spark.Request;
import spark.Response;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Created by 00013708 on 2017/3/13.
 */
public class BraveSparkResponseFilter implements Filter {

    public static BraveSparkResponseFilter create(Brave brave) {
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

        public BraveSparkResponseFilter.Builder spanNameProvider(SpanNameProvider spanNameProvider) {
            this.spanNameProvider = checkNotNull(spanNameProvider, "spanNameProvider");
            return this;
        }

        public BraveSparkResponseFilter build() {
            return new BraveSparkResponseFilter(this);
        }
    }


    private final ServerResponseInterceptor responseInterceptor;
    private final ServerSpanThreadBinder serverThreadBinder;
    private final SpanNameProvider spanNameProvider;

    BraveSparkResponseFilter(Builder b) { // intentionally hidden
        this.responseInterceptor = b.brave.serverResponseInterceptor();
        this.serverThreadBinder = b.brave.serverSpanThreadBinder();
        this.spanNameProvider = b.spanNameProvider;
    }


    @Override
    public void handle(Request request, Response response) throws Exception {
        this.responseInterceptor.handle(new HttpServerResponseAdapter(new SparkHttpServerResponse(response)));
    }

}
