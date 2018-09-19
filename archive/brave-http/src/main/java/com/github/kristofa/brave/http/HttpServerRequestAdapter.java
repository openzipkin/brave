package com.github.kristofa.brave.http;

import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.ServerRequestAdapter;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.TraceData;
import java.util.Collection;
import java.util.Collections;

import static com.github.kristofa.brave.IdConversion.convertToLong;

/**
 * @deprecated Replaced by {@code HttpServerParser} from brave-http
 */
@Deprecated
public class HttpServerRequestAdapter implements ServerRequestAdapter {
    private final HttpServerRequest request;
    private final SpanNameProvider spanNameProvider;

    public HttpServerRequestAdapter(HttpServerRequest request, SpanNameProvider spanNameProvider) {
        this.request = request;
        this.spanNameProvider = spanNameProvider;
    }

    @Override
    public TraceData getTraceData() {
        // try to extract single-header format
        String b3String = request.getHttpHeaderValue("b3");
        if (b3String != null) {
            TraceData extracted = B3SingleFormat.parseB3SingleFormat(b3String);
            if (extracted != null) return extracted;
        }

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
        } else if (parsedSampled) {
            return TraceData.SAMPLED;
        } else {
            return TraceData.NOT_SAMPLED;
        }
    }

    @Override
    public String getSpanName() {
        return spanNameProvider.spanName(request);
    }

    @Override
    public Collection<KeyValueAnnotation> requestAnnotations() {
        KeyValueAnnotation uriAnnotation = KeyValueAnnotation.create(
                "http.url", request.getUri().toString());
        return Collections.singleton(uriAnnotation);
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
