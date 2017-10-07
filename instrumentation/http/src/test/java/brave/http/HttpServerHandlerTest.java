package brave.http;

import brave.Tracer;
import brave.Tracing;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import zipkin2.Endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpServerHandlerTest {
  Tracer tracer;
  @Mock HttpSampler sampler;
  @Mock HttpServerAdapter<Object, Object> adapter;
  @Mock TraceContext.Extractor<Object> extractor;
  @Mock brave.Span span;
  Object request = new Object();
  Object response = new Object();
  HttpServerHandler<Object, Object> handler;

  @Before public void init() {
    HttpTracing httpTracing = HttpTracing.newBuilder(Tracing.newBuilder().build())
        .serverSampler(sampler).build();
    tracer = httpTracing.tracing().tracer();
    handler = HttpServerHandler.create(httpTracing, adapter);

    when(adapter.method(request)).thenReturn("GET");
    when(adapter.parseClientAddress(eq(request), isA(Endpoint.Builder.class))).thenCallRealMethod();
  }

  @After public void close(){
    Tracing.current().close();
  }

  @Test public void handleReceive_defaultsToMakeNewTrace() {
    when(extractor.extract(request))
        .thenReturn(TraceContextOrSamplingFlags.create(SamplingFlags.EMPTY));

    // request sampler abstains (trace ID sampler will say true)
    when(sampler.trySample(adapter, request)).thenReturn(null);

    assertThat(handler.handleReceive(extractor, request).isNoop())
        .isFalse();
  }

  @Test public void handleReceive_reusesTraceId() {
    HttpTracing httpTracing = HttpTracing.create(Tracing.newBuilder()
        .supportsJoin(false).build());

    tracer = httpTracing.tracing().tracer();
    handler = HttpServerHandler.create(httpTracing, adapter);

    TraceContext incomingContext = tracer.nextSpan().context();
    when(extractor.extract(request))
        .thenReturn(TraceContextOrSamplingFlags.create(incomingContext));

    assertThat(handler.handleReceive(extractor, request).context())
        .extracting(TraceContext::traceId, TraceContext::parentId, TraceContext::shared)
        .containsOnly(incomingContext.traceId(), incomingContext.spanId(), false);
  }

  @Test public void handleReceive_reusesSpanIds() {
    TraceContext incomingContext = tracer.nextSpan().context();
    when(extractor.extract(request))
        .thenReturn(TraceContextOrSamplingFlags.create(incomingContext));

    assertThat(handler.handleReceive(extractor, request).context())
        .isEqualTo(incomingContext.toBuilder().shared(true).build());
  }

  @Test public void handleReceive_honorsSamplingFlags() {
    when(extractor.extract(request))
        .thenReturn(TraceContextOrSamplingFlags.create(SamplingFlags.NOT_SAMPLED));

    assertThat(handler.handleReceive(extractor, request).isNoop())
        .isTrue();
  }

  @Test public void handleReceive_makesRequestBasedSamplingDecision_flags() {
    when(extractor.extract(request))
        .thenReturn(TraceContextOrSamplingFlags.create(SamplingFlags.EMPTY));

    // request sampler says false eventhough trace ID sampler would have said true
    when(sampler.trySample(adapter, request)).thenReturn(false);

    assertThat(handler.handleReceive(extractor, request).isNoop())
        .isTrue();
  }


  @Test public void handleReceive_makesRequestBasedSamplingDecision_context() {
    TraceContext incomingContext = tracer.nextSpan().context().toBuilder().sampled(null).build();
    when(extractor.extract(request))
        .thenReturn(TraceContextOrSamplingFlags.create(incomingContext));

    // request sampler says false eventhough trace ID sampler would have said true
    when(sampler.trySample(adapter, request)).thenReturn(false);

    assertThat(handler.handleReceive(extractor, request).isNoop())
        .isTrue();
  }

  @Test public void handleSend_nothingOnNoop_success() {
    when(span.isNoop()).thenReturn(true);

    handler.handleSend(response, null, span);

    verify(span, never()).finish();
  }

  @Test public void handleSend_nothingOnNoop_error() {
    when(span.isNoop()).thenReturn(true);

    handler.handleSend(null, new RuntimeException("drat"), span);

    verify(span, never()).finish();
  }

  @Test public void handleSend_finishedEvenIfAdapterThrows() {
    when(adapter.statusCode(response)).thenThrow(new RuntimeException());

    try {
      handler.handleSend(response, null, span);
      failBecauseExceptionWasNotThrown(RuntimeException.class);
    } catch (RuntimeException e) {
      verify(span).finish();
    }
  }
}
