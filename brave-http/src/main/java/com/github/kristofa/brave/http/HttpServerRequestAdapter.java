package com.github.kristofa.brave.http;

import com.github.kristofa.brave.ServerRequestAdapter;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.TraceData;

import static com.github.kristofa.brave.IdConversion.convertToLong;

public class HttpServerRequestAdapter extends HttpRequestAdapter<HttpServerRequest>
    implements ServerRequestAdapter {

    public static FactoryBuilder factoryBuilder() {
        return new FactoryBuilder();
    }

    public static final class FactoryBuilder
        extends HttpRequestAdapter.FactoryBuilder<FactoryBuilder> {

        public <R extends HttpServerRequest> ServerRequestAdapter.Factory<R> build(
            Class<? extends R> requestType) {
            return new Factory(this, requestType);
        }

        FactoryBuilder() { // intentionally hidden
        }
    }

    static final class Factory<R extends HttpServerRequest>
        extends HttpRequestAdapter.Factory<R, ServerRequestAdapter>
        implements ServerRequestAdapter.Factory<R> {

        Factory(FactoryBuilder builder, Class<R> requestType) {
            super(builder, requestType);
        }

        @Override public ServerRequestAdapter create(R request) {
            return new HttpServerRequestAdapter(this, request);
        }
    }

    /**
     * @deprecated please use {@link #factoryBuilder()}
     */
    @Deprecated
    public HttpServerRequestAdapter(HttpServerRequest request, SpanNameProvider spanNameProvider) {
        this((Factory) factoryBuilder().spanNameProvider(spanNameProvider)
                .build(HttpServerRequest.class),
            request);
    }

    HttpServerRequestAdapter(Factory factory, HttpServerRequest request) { // intentionally hidden
        super(factory, request);
    }

    @Override
    public TraceData getTraceData() {
        String sampled = request.getHttpHeaderValue(BraveHttpHeaders.Sampled.getName());
        String parentSpanId = request.getHttpHeaderValue(BraveHttpHeaders.ParentSpanId.getName());
        String traceId = request.getHttpHeaderValue(BraveHttpHeaders.TraceId.getName());
        String spanId = request.getHttpHeaderValue(BraveHttpHeaders.SpanId.getName());

        // Official sampled value is 1, though some old instrumentation send true
        Boolean parsedSampled = sampled != null
            ? sampled.equals("1") || sampled.equalsIgnoreCase("true")
            : null;

        if (traceId != null && spanId != null) {
            return TraceData.create(getSpanId(traceId, spanId, parentSpanId, parsedSampled));
        } else if (parsedSampled == null) {
            return TraceData.EMPTY;
        } else if (parsedSampled.booleanValue()) {
            // Invalid: The caller requests the trace to be sampled, but didn't pass IDs
            return TraceData.EMPTY;
        } else {
            return TraceData.NOT_SAMPLED;
        }
    }

    static SpanId getSpanId(String traceId, String spanId, String parentSpanId, Boolean sampled) {
        return SpanId.builder()
            .traceIdHigh(traceId.length() == 32 ? convertToLong(traceId, 0) : 0)
            .traceId(convertToLong(traceId))
            .spanId(convertToLong(spanId))
            .sampled(sampled)
            .parentId(parentSpanId == null ? null : convertToLong(parentSpanId)).build();
   }
}
