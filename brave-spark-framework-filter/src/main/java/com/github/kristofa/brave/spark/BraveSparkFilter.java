package com.github.kristofa.brave.spark;

import com.github.kristofa.brave.*;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.HttpServerRequestAdapter;
import com.github.kristofa.brave.http.HttpServerResponseAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.github.kristofa.brave.spark.internal.MaybeAddClientAddressFromRequest;
import spark.Filter;
import spark.Request;
import spark.Response;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

public final class BraveSparkFilter {

    public static BraveSparkFilter create(Brave brave) {
        return new BraveSparkFilter.Builder(brave).build();
    }

    public static BraveSparkFilter.Builder builder(Brave brave) {
        return new BraveSparkFilter.Builder(brave);
    }

    public static final class Builder {
        final Brave brave;
        SpanNameProvider spanNameProvider = new DefaultSpanNameProvider();

        Builder(Brave brave) { // intentionally hidden
            this.brave = checkNotNull(brave, "brave");
        }

        public BraveSparkFilter.Builder spanNameProvider(SpanNameProvider spanNameProvider) {
            this.spanNameProvider = checkNotNull(spanNameProvider, "spanNameProvider");
            return this;
        }

        public BraveSparkFilter build() {
            return new BraveSparkFilter(this);
        }
    }

    private final ServerRequestInterceptor requestInterceptor;
    private final ServerResponseInterceptor responseInterceptor;
    private final ServerSpanThreadBinder serverThreadBinder;
    private final SpanNameProvider spanNameProvider;
    private final ServerTracer serverTracer;
    private final MaybeAddClientAddressFromRequest maybeAddClientAddressFromRequest;

    BraveSparkFilter(BraveSparkFilter.Builder b) { // intentionally hidden
        this.requestInterceptor = b.brave.serverRequestInterceptor();
        this.responseInterceptor = b.brave.serverResponseInterceptor();
        this.serverThreadBinder = b.brave.serverSpanThreadBinder();
        this.spanNameProvider = b.spanNameProvider;
        this.serverTracer = b.brave.serverTracer();
        maybeAddClientAddressFromRequest = MaybeAddClientAddressFromRequest.create(b.brave);
    }

    public Filter requestFilter() {
        return new Filter() {
            @Override
            public void handle(Request request, Response response) throws Exception {
                requestInterceptor.handle(new HttpServerRequestAdapter(new SparkHttpServerRequest(request), spanNameProvider));
                maybeAddClientAddressFromRequest.accept(request.raw());
            }
        };
    }

    public Filter responseFilter() {
        return new Filter() {
            @Override
            public void handle(Request request, Response response) throws Exception {
                responseInterceptor.handle(new HttpServerResponseAdapter(new SparkHttpServerResponse(response)));
            }
        };
    }
}
