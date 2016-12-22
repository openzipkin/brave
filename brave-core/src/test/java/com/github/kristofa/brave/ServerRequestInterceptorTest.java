package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import org.junit.Test;
import zipkin.reporter.Reporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ServerRequestInterceptorTest {

    private final static String SPAN_NAME = "getUsers";
    private final static long TRACE_ID = 3425;
    private final static long SPAN_ID = 43435;
    private final static long PARENT_SPAN_ID = 44334435;
    private static final KeyValueAnnotation ANNOTATION1 = KeyValueAnnotation.create(zipkin.TraceKeys.HTTP_URL, "/orders/user/4543");
    private static final KeyValueAnnotation ANNOTATION2 = KeyValueAnnotation.create("http.code", "200");

    InheritableServerClientAndLocalSpanState state =
        new InheritableServerClientAndLocalSpanState(mock(Endpoint.class));
    ServerTracer serverTracer = new AutoValue_ServerTracer.Builder()
        .state(state)
        .randomGenerator(mock(Random.class))
        .reporter(Reporter.NOOP)
        .traceSampler(Sampler.ALWAYS_SAMPLE)
        .clock(AnnotationSubmitter.DefaultClock.INSTANCE)
        .traceId128Bit(false)
        .build();
    ServerRequestInterceptor interceptor = new ServerRequestInterceptor(serverTracer);
    ServerRequestAdapter adapter = mock(ServerRequestAdapter.class);

    @Test
    public void handleSampleFalse() {
        when(adapter.getTraceData()).thenReturn(TraceData.NOT_SAMPLED);
        interceptor.handle(adapter);

        assertThat(serverTracer.spanAndEndpoint().state().getCurrentServerSpan())
            .isEqualTo(ServerSpan.NOT_SAMPLED);
    }

    @Test
    public void handleNoState_whenSampleTrue() {
        when(adapter.getTraceData()).thenReturn(TraceData.EMPTY);
        when(adapter.getSpanName()).thenReturn(SPAN_NAME);
        when(adapter.requestAnnotations()).thenReturn(Collections.EMPTY_LIST);

        interceptor.handle(adapter);

        ServerSpan span = serverTracer.spanAndEndpoint().state().getCurrentServerSpan();
        assertThat(span.getSpan().getName())
            .isEqualTo(SPAN_NAME.toLowerCase());
        assertThat(span.getSample())
            .isTrue();
    }

    @Test
    public void handleSampleRequestWithParentSpanId() {
        SpanId spanId = SpanId.builder()
            .traceId(TRACE_ID).spanId(SPAN_ID).parentId(PARENT_SPAN_ID).sampled(true).build();
        when(adapter.getTraceData()).thenReturn(TraceData.create(spanId));
        when(adapter.getSpanName()).thenReturn(SPAN_NAME);
        when(adapter.requestAnnotations()).thenReturn(Arrays.asList(ANNOTATION1, ANNOTATION2));

        interceptor.handle(adapter);

        Span span = state.getCurrentServerSpan().getSpan();
        assertThat(span.getTrace_id())
            .isEqualTo(TRACE_ID);
        assertThat(span.getId())
            .isEqualTo(SPAN_ID);
        assertThat(span.getParent_id())
            .isEqualTo(PARENT_SPAN_ID);
        assertThat(span.getAnnotations())
            .extracting(a -> a.value).containsExactly("sr");
        assertThat(span.getBinary_annotations())
            .extracting(a -> a.key).containsExactly(ANNOTATION1.getKey(), ANNOTATION2.getKey());
    }

    @Test
    public void handle_clientOriginatedRootSpan_doesntSetTimestamp() {
        // When client-originated, sampled flag must be set
        SpanId spanId = SpanId.builder()
            .traceId(TRACE_ID).spanId(SPAN_ID).parentId(null).sampled(true).build();

        when(adapter.getTraceData()).thenReturn(TraceData.create(spanId));
        when(adapter.getSpanName()).thenReturn(SPAN_NAME);
        when(adapter.requestAnnotations()).thenReturn(Arrays.asList(ANNOTATION1, ANNOTATION2));

        interceptor.handle(adapter);

        // don't log timestamp when span is client-originated
        Span span = state.getCurrentServerSpan().getSpan();
        assertThat(span.getTimestamp()).isNull();
    }

    @Test
    public void handle_externallyProvisionedIds_setsTimestamp() {
        // Those only controlling IDs leave sampled flag unset
        SpanId spanId = SpanId.builder().traceId(TRACE_ID).spanId(SPAN_ID).parentId(null).build();

        when(adapter.getTraceData()).thenReturn(TraceData.create(spanId));
        when(adapter.getSpanName()).thenReturn(SPAN_NAME);
        when(adapter.requestAnnotations()).thenReturn(Arrays.asList(ANNOTATION1, ANNOTATION2));

        interceptor.handle(adapter);

        // We originated the trace, so we should set the timestamp
        Span span = state.getCurrentServerSpan().getSpan();
        assertThat(span.getTimestamp()).isNotNull();
    }

    @Test
    public void handle_externallyProvisionedIds_localSample_false() {
        serverTracer = new AutoValue_ServerTracer.Builder(serverTracer)
            .traceSampler(Sampler.NEVER_SAMPLE)
            .build();
        interceptor = new ServerRequestInterceptor(serverTracer);

        // Those only controlling IDs leave sampled flag unset
        SpanId spanId = SpanId.builder().traceId(TRACE_ID).spanId(SPAN_ID).parentId(null).build();

        when(adapter.getTraceData()).thenReturn(TraceData.create(spanId));
        interceptor.handle(adapter);

        assertThat(serverTracer.spanAndEndpoint().state().getCurrentServerSpan())
            .isEqualTo(ServerSpan.NOT_SAMPLED);
    }
}
