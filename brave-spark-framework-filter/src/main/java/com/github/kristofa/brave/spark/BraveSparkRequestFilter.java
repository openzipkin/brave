package com.github.kristofa.brave.spark;


import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.ServerSpan;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.HttpServerRequestAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.github.kristofa.brave.spark.internal.MaybeAddClientAddressFromRequest;
import spark.Filter;
import spark.Request;
import spark.Response;

import static com.github.kristofa.brave.internal.Util.checkNotNull;


public class BraveSparkRequestFilter implements Filter {

    public static BraveSparkRequestFilter create(Brave brave) {
        return new BraveSparkRequestFilter.Builder(brave).build();
    }

    public static BraveSparkRequestFilter.Builder builder(Brave brave) {
        return new BraveSparkRequestFilter.Builder(brave);
    }

    public static final class Builder {
        final Brave brave;
        SpanNameProvider spanNameProvider = new DefaultSpanNameProvider();

        Builder(Brave brave) { // intentionally hidden
            this.brave = checkNotNull(brave, "brave");
        }

        public BraveSparkRequestFilter.Builder spanNameProvider(SpanNameProvider spanNameProvider) {
            this.spanNameProvider = checkNotNull(spanNameProvider, "spanNameProvider");
            return this;
        }

        public BraveSparkRequestFilter build() {
            return new BraveSparkRequestFilter(this);
        }
    }

    private final ServerRequestInterceptor requestInterceptor;
    private final ServerSpanThreadBinder serverThreadBinder;
    private final SpanNameProvider spanNameProvider;
    private final MaybeAddClientAddressFromRequest maybeAddClientAddressFromRequest;

    BraveSparkRequestFilter(Builder b) { // intentionally hidden
        this.requestInterceptor = b.brave.serverRequestInterceptor();
        this.serverThreadBinder = b.brave.serverSpanThreadBinder();
        this.spanNameProvider = b.spanNameProvider;
        maybeAddClientAddressFromRequest = MaybeAddClientAddressFromRequest.create(b.brave);
    }

    @Override
    public void handle(Request request, Response response) throws Exception {
        this.requestInterceptor.handle(new HttpServerRequestAdapter(new SparkHttpServerRequest(request), this.spanNameProvider));
        if (maybeAddClientAddressFromRequest != null) {
            maybeAddClientAddressFromRequest.accept(request.raw());
        }
        ServerSpan serverSpan =  serverThreadBinder.getCurrentServerSpan();
        System.out.println("REQ serverSpan=>"+serverSpan.toString());
    }
}

