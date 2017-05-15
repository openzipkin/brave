package brave.http;

import brave.Tracer;
import brave.Tracing;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import zipkin.Constants;
import zipkin.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpClientHandlerTest {
  List<Span> spans = new ArrayList<>();
  HttpTracing httpTracing;
  @Mock HttpSampler sampler;
  @Mock HttpClientAdapter<Object, Object> adapter;
  @Mock TraceContext.Injector<Object> injector;
  @Mock brave.Span span;
  Object request = new Object();
  Object response = new Object();
  HttpClientHandler<Object, Object> handler;

  @Before public void init() {
    httpTracing = HttpTracing.newBuilder(Tracing.newBuilder().reporter(spans::add).build())
        .clientSampler(sampler).build();
    handler = HttpClientHandler.create(httpTracing, adapter);

    when(adapter.method(request)).thenReturn("GET");
  }

  @Test public void handleSend_defaultsToMakeNewTrace() {
    // request sampler abstains (trace ID sampler will say true)
    when(sampler.trySample(adapter, request)).thenReturn(null);

    assertThat(handler.handleSend(injector, request))
        .extracting(s -> s.isNoop(), s -> s.context().parentId())
        .containsExactly(false, null);
  }

  @Test public void handleSend_makesAChild() {
    brave.Span parent = httpTracing.tracing().tracer().newTrace();
    try (Tracer.SpanInScope ws = httpTracing.tracing().tracer().withSpanInScope(parent)) {
      assertThat(handler.handleSend(injector, request))
          .extracting(s -> s.isNoop(), s -> s.context().parentId())
          .containsExactly(false, parent.context().spanId());
    }
  }

  @Test public void handleSend_makesRequestBasedSamplingDecision() {
    // request sampler says false eventhough trace ID sampler would have said true
    when(sampler.trySample(adapter, request)).thenReturn(false);

    assertThat(handler.handleSend(injector, request).isNoop())
        .isTrue();
  }

  @Test public void handleSend_injectsTheTraceContext() {
    TraceContext context = handler.handleSend(injector, request).context();

    verify(injector).inject(context, request);
  }

  @Test public void handleSend_injectsTheTraceContext_onTheCarrier() {
    Object customCarrier = new Object();
    TraceContext context = handler.handleSend(injector, customCarrier, request).context();

    verify(injector).inject(context, customCarrier);
  }

  @Test public void handleSend_addsClientAddressWhenOnlyServiceName() {
    httpTracing = httpTracing.clientOf("remote-service");

    // request sampler abstains (trace ID sampler will say true)
    when(sampler.trySample(adapter, request)).thenReturn(null);
    HttpClientHandler.create(httpTracing, adapter).handleSend(injector, request).finish();

    assertThat(spans)
        .flatExtracting(s -> s.binaryAnnotations)
        .filteredOn(b -> b.key.equals(Constants.SERVER_ADDR))
        .extracting(b -> b.endpoint.serviceName)
        .containsExactly("remote-service");
  }

  @Test public void handleSend_skipsClientAddressWhenUnparsed() {
    handler.handleSend(injector, request).finish();

    assertThat(spans)
        .flatExtracting(s -> s.binaryAnnotations)
        .filteredOn(b -> b.key.equals(Constants.SERVER_ADDR))
        .isEmpty();
  }

  @Test public void handleReceive_nothingOnNoop_success() {
    when(span.isNoop()).thenReturn(true);

    handler.handleReceive(response, null, span);

    verify(span, never()).finish();
  }

  @Test public void handleReceive_nothingOnNoop_error() {
    when(span.isNoop()).thenReturn(true);

    handler.handleReceive(null, new RuntimeException("drat"), span);

    verify(span, never()).finish();
  }

  @Test public void handleReceive_finishedEvenIfAdapterThrows() {
    when(adapter.statusCode(response)).thenThrow(new RuntimeException());

    try {
      handler.handleReceive(response, null, span);
      failBecauseExceptionWasNotThrown(RuntimeException.class);
    } catch (RuntimeException e) {
      verify(span).finish();
    }
  }
}
