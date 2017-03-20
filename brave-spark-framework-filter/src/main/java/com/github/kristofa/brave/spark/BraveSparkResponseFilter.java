package com.github.kristofa.brave.spark;

import com.github.kristofa.brave.*;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.HttpServerResponseAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import spark.Filter;
import spark.Request;
import spark.Response;

import static com.github.kristofa.brave.internal.Util.checkNotNull;


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
    private final ServerTracer serverTracer;

    BraveSparkResponseFilter(Builder b) { // intentionally hidden
        this.responseInterceptor = b.brave.serverResponseInterceptor();
        this.serverThreadBinder = b.brave.serverSpanThreadBinder();
        this.spanNameProvider = b.spanNameProvider;
        this.serverTracer = b.brave.serverTracer();
    }


    @Override
    public void handle(Request request, Response response) throws Exception {
        int status = response.raw().getStatus();
        System.out.println("status=>"+status);
        ServerSpan serverSpan =  serverThreadBinder.getCurrentServerSpan();
        System.out.println("RESP serverSpan=>"+serverSpan.toString());
        serverTracer.submitBinaryAnnotation(TraceKeys.HTTP_STATUS_CODE, String.valueOf(404));

        this.responseInterceptor.handle(new HttpServerResponseAdapter(new SparkHttpServerResponse(response)));
    }
}
