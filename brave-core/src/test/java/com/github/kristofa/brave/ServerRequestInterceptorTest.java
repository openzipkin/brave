package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Endpoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ServerRequestInterceptorTest {

    private final static String SPAN_NAME = "getUsers";
    private final static long TRACE_ID = 3425;
    private final static long SPAN_ID = 43435;
    private final static long PARENT_SPAN_ID = 44334435;
    private static final Endpoint ENDPOINT = Endpoint.create("serviceName", 80);
    private static final KeyValueAnnotation ANNOTATION1 = KeyValueAnnotation.create(zipkin.TraceKeys.HTTP_URL, "/orders/user/4543");
    private static final KeyValueAnnotation ANNOTATION2 = KeyValueAnnotation.create("http.code", "200");

    List<zipkin.Span> spans = new ArrayList<>();
    Brave brave = new Brave.Builder(ENDPOINT).reporter(spans::add).build();
    Recorder recorder = brave.serverTracer().recorder();
    ServerRequestInterceptor interceptor = new ServerRequestInterceptor(brave.serverTracer());

    ServerRequestAdapter adapter = mock(ServerRequestAdapter.class);

    @Before
    public void setup() {
        ThreadLocalServerClientAndLocalSpanState.clear();
    }

    @Test
    public void handleSampleFalse() {
        when(adapter.getTraceData()).thenReturn(TraceData.NOT_SAMPLED);
        interceptor.handle(adapter);

        assertThat(brave.serverSpanThreadBinder().getCurrentServerSpan())
            .isEqualTo(ServerSpan.NOT_SAMPLED);
    }

    @Test
    public void handleNoState_whenSampleTrue() {
        when(adapter.getTraceData()).thenReturn(TraceData.EMPTY);
        when(adapter.getSpanName()).thenReturn(SPAN_NAME);
        when(adapter.requestAnnotations()).thenReturn(Collections.EMPTY_LIST);

        interceptor.handle(adapter);
        recorder.flush(brave.serverTracer().currentSpan().get());

        assertThat(brave.serverTracer().currentSpan().sampled())
            .isTrue();

        assertThat(spans.get(0).name)
            .isEqualTo(SPAN_NAME.toLowerCase());
    }

    @Test
    public void handleSampleRequestWithParentSpanId() {
        SpanId spanId = SpanId.builder()
            .traceId(TRACE_ID).spanId(SPAN_ID).parentId(PARENT_SPAN_ID).sampled(true).build();
        when(adapter.getTraceData()).thenReturn(TraceData.create(spanId));
        when(adapter.getSpanName()).thenReturn(SPAN_NAME);
        when(adapter.requestAnnotations()).thenReturn(Arrays.asList(ANNOTATION1, ANNOTATION2));

        interceptor.handle(adapter);
        recorder.flush(brave.serverTracer().currentSpan().get());

        zipkin.Span span = spans.get(0);
        assertThat(span.traceId)
            .isEqualTo(TRACE_ID);
        assertThat(span.id)
            .isEqualTo(SPAN_ID);
        assertThat(span.parentId)
            .isEqualTo(PARENT_SPAN_ID);
        assertThat(span.annotations)
            .extracting(a -> a.value).containsExactly("sr");
        assertThat(span.binaryAnnotations)
            .extracting(a -> a.key).containsExactly(ANNOTATION2.getKey(), ANNOTATION1.getKey());
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
        recorder.flush(brave.serverTracer().currentSpan().get());

        // don't log timestamp when span is client-originated
        assertThat(spans.get(0).timestamp).isNull();
    }

    @Test
    public void handle_externallyProvisionedIds_setsTimestamp() {
        // Those only controlling IDs leave sampled flag unset
        SpanId spanId = SpanId.builder().traceId(TRACE_ID).spanId(SPAN_ID).parentId(null).build();

        when(adapter.getTraceData()).thenReturn(TraceData.create(spanId));
        when(adapter.getSpanName()).thenReturn(SPAN_NAME);
        when(adapter.requestAnnotations()).thenReturn(Arrays.asList(ANNOTATION1, ANNOTATION2));

        interceptor.handle(adapter);
        recorder.flush(brave.serverTracer().currentSpan().get());

        // We originated the trace, so we should set the timestamp
        assertThat(spans.get(0).timestamp).isNotNull();
    }

    @Test
    public void handle_externallyProvisionedIds_localSample_false() {
        brave = new Brave.Builder().traceSampler(Sampler.NEVER_SAMPLE).build();
        interceptor = new ServerRequestInterceptor(brave.serverTracer());

        // Those only controlling IDs leave sampled flag unset
        SpanId spanId = SpanId.builder().traceId(TRACE_ID).spanId(SPAN_ID).parentId(null).build();

        when(adapter.getTraceData()).thenReturn(TraceData.create(spanId));
        interceptor.handle(adapter);

        assertThat(brave.serverSpanThreadBinder().getCurrentServerSpan())
            .isEqualTo(ServerSpan.NOT_SAMPLED);
    }
}
