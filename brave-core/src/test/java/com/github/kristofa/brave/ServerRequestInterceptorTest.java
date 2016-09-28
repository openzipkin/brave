package com.github.kristofa.brave;


import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import java.util.Random;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.Collections;

import static com.github.kristofa.brave.Sampler.ALWAYS_SAMPLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class ServerRequestInterceptorTest {

    private final static String SPAN_NAME = "getUsers";
    private final static long TRACE_ID = 3425;
    private final static long SPAN_ID = 43435;
    private final static long PARENT_SPAN_ID = 44334435;
    private static final KeyValueAnnotation ANNOTATION1 = KeyValueAnnotation.create(zipkin.TraceKeys.HTTP_URL, "/orders/user/4543");
    private static final KeyValueAnnotation ANNOTATION2 = KeyValueAnnotation.create("http.code", "200");

    private ServerRequestInterceptor interceptor;
    private ServerTracer serverTracer;
    private ServerRequestAdapter adapter;

    @Before
    public void setup() {
        serverTracer = mock(ServerTracer.class);
        interceptor = new ServerRequestInterceptor(serverTracer);
        adapter = mock(ServerRequestAdapter.class);
    }

    @Test
    public void handleSampleFalse() {
        TraceData traceData = TraceData.builder().sample(false).build();
        when(adapter.getTraceData()).thenReturn(traceData);
        interceptor.handle(adapter);
        InOrder inOrder = inOrder(serverTracer);
        inOrder.verify(serverTracer).clearCurrentSpan();
        inOrder.verify(serverTracer).setStateNoTracing();
        verifyNoMoreInteractions(serverTracer);
    }

    @Test
    public void handleNoState() {
        TraceData traceData = TraceData.builder().build();
        when(adapter.getTraceData()).thenReturn(traceData);
        when(adapter.getSpanName()).thenReturn(SPAN_NAME);
        when(adapter.requestAnnotations()).thenReturn(Collections.EMPTY_LIST);

        interceptor.handle(adapter);
        InOrder inOrder = inOrder(serverTracer);
        inOrder.verify(serverTracer).clearCurrentSpan();
        inOrder.verify(serverTracer).setStateUnknown(SPAN_NAME);
        inOrder.verify(serverTracer).setServerReceived();
        verifyNoMoreInteractions(serverTracer);
    }

    @Test
    public void handleSampleRequestWithParentSpanId() {
        InheritableServerClientAndLocalSpanState state =
            new InheritableServerClientAndLocalSpanState(mock(Endpoint.class));
        serverTracer = ServerTracer.builder()
            .state(state)
            .randomGenerator(mock(Random.class))
            .spanCollector(mock(SpanCollector.class))
            .traceSampler(Sampler.ALWAYS_SAMPLE)
            .clock(AnnotationSubmitter.DefaultClock.INSTANCE)
            .build();
        interceptor = new ServerRequestInterceptor(serverTracer);

        SpanId spanId =
            SpanId.builder().traceId(TRACE_ID).spanId(SPAN_ID).parentId(PARENT_SPAN_ID).build();
        TraceData traceData = TraceData.builder().spanId(spanId).sample(true).build();
        when(adapter.getTraceData()).thenReturn(traceData);
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

        // don't log timestamp when span is client-originated
        assertThat(span.getTimestamp()).isNull();
        assertThat(span.startTick).isNull();
    }

    @Test
    public void handleSampleRequestWithoutParentSpanId() {
        InheritableServerClientAndLocalSpanState state =
            new InheritableServerClientAndLocalSpanState(mock(Endpoint.class));
        serverTracer = ServerTracer.builder()
            .state(state)
            .randomGenerator(mock(Random.class))
            .spanCollector(mock(SpanCollector.class))
            .traceSampler(Sampler.ALWAYS_SAMPLE)
            .clock(AnnotationSubmitter.DefaultClock.INSTANCE)
            .build();
        interceptor = new ServerRequestInterceptor(serverTracer);

        SpanId spanId =
            SpanId.builder().traceId(TRACE_ID).spanId(SPAN_ID).parentId(null).build();
        TraceData traceData = TraceData.builder().spanId(spanId).sample(true).build();
        when(adapter.getTraceData()).thenReturn(traceData);
        when(adapter.getSpanName()).thenReturn(SPAN_NAME);
        when(adapter.requestAnnotations()).thenReturn(Arrays.asList(ANNOTATION1, ANNOTATION2));

        interceptor.handle(adapter);

        Span span = state.getCurrentServerSpan().getSpan();
        assertThat(span.getTrace_id())
            .isEqualTo(TRACE_ID);
        assertThat(span.getId())
            .isEqualTo(SPAN_ID);
        assertThat(span.getParent_id())
            .isNull();
        assertThat(span.getAnnotations())
            .extracting(a -> a.value).containsExactly("sr");
        assertThat(span.getBinary_annotations())
            .extracting(a -> a.key).containsExactly(ANNOTATION1.getKey(), ANNOTATION2.getKey());

        // don't log timestamp when span is client-originated
        assertThat(span.getTimestamp()).isNull();
        assertThat(span.startTick).isNull();
    }
}
