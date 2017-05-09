package brave.http;

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
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpClientHandlerTest {
  List<Span> spans = new ArrayList<>();
  HttpTracing httpTracing;
  @Mock HttpClientAdapter<Object, Object> adapter;
  @Mock TraceContext.Injector<Object> injector;
  @Mock brave.Span span;
  Object request = new Object();
  Object response = new Object();
  HttpClientHandler<Object, Object> handler;

  @Before public void init() {
    httpTracing = HttpTracing.create(Tracing.newBuilder().reporter(spans::add).build());
    handler = HttpClientHandler.create(httpTracing, adapter);

    when(adapter.method(request)).thenReturn("GET");
    when(adapter.parseError(eq(response), anyObject())).thenCallRealMethod();
  }

  @Test public void handleSend_defaultsToMakeNewTrace() {
    assertThat(handler.handleSend(injector, request))
        .extracting(s -> s.isNoop(), s -> s.context().parentId())
        .containsExactly(false, null);
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
    httpTracing = httpTracing.toBuilder().serverName("remote-service").build();
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

  @Test public void handleReceive_doesntErrorOnRedirect() {
    when(adapter.statusCode(response)).thenReturn(302);

    handler.handleReceive(response, null, span);

    verify(span, never()).tag("error", "302");
  }

  @Test public void handleReceive_tagsErrorOnResponseCode() {
    when(adapter.statusCode(response)).thenReturn(400);

    handler.handleReceive(response, null, span);

    verify(span).tag("error", "400");
  }

  @Test public void handleReceive_tagsErrorPrefersExceptionVsResponseCode() {
    when(adapter.statusCode(response)).thenReturn(400);

    handler.handleReceive(response, new RuntimeException("drat"), span);

    verify(span).tag("error", "drat");
  }
}
