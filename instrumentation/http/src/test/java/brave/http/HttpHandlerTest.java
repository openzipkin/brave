package brave.http;

import brave.SpanCustomizer;
import brave.propagation.CurrentTraceContext;
import brave.propagation.StrictCurrentTraceContext;
import brave.propagation.TraceContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import zipkin2.Endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpHandlerTest {
  CurrentTraceContext currentTraceContext = new StrictCurrentTraceContext();
  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(10L).build();
  @Mock HttpAdapter<Object, Object> adapter;
  @Mock brave.Span span;
  @Mock SpanCustomizer spanCustomizer;
  Object request = new Object(), response = new Object();
  HttpHandler<Object, Object, HttpAdapter<Object, Object>> handler;

  @Before public void init() {
    handler = new HttpHandler(currentTraceContext, adapter, new HttpParser()) {
      @Override boolean parseRemoteEndpoint(Object request, Endpoint.Builder remoteEndpoint) {
        return false;
      }
    };
    when(span.context()).thenReturn(context);
    when(span.customizer()).thenReturn(spanCustomizer);
  }

  @Test public void handleStart_nothingOnNoop_success() {
    when(span.isNoop()).thenReturn(true);

    handler.handleStart(request, span);

    verify(span, never()).start();
  }

  @Test public void handleStart_parsesTagsWithCustomizer() {
    when(adapter.method(request)).thenReturn("GET");
    when(span.customizer()).thenReturn(spanCustomizer);

    handler.handleStart(request, span);

    verify(spanCustomizer).name("GET");
    verify(spanCustomizer).tag("http.method", "GET");
    verifyNoMoreInteractions(spanCustomizer);
  }

  @Test public void handleStart_parsesTagsInScope() {
    handler = new HttpHandler(currentTraceContext, adapter, new HttpParser() {
      @Override
      public <Req> void request(HttpAdapter<Req, ?> adapter, Req req, SpanCustomizer customizer) {
        assertThat(currentTraceContext.get()).isNotNull();
      }
    }) {
      @Override boolean parseRemoteEndpoint(Object request, Endpoint.Builder remoteEndpoint) {
        return false;
      }
    };

    handler.handleStart(request, span);
  }

  @Test public void handleStart_skipsRemoteEndpointWhenNotParsed() {
    handler = new HttpHandler(currentTraceContext, adapter, new HttpParser()) {
      @Override boolean parseRemoteEndpoint(Object request, Endpoint.Builder remoteEndpoint) {
        return false;
      }
    };

    handler.handleStart(request, span);

    verify(span, never()).remoteEndpoint(any(Endpoint.class));
  }

  @Test public void handleStart_addsRemoteEndpointWhenParsed() {
    handler = new HttpHandler(currentTraceContext, adapter, new HttpParser()) {
      @Override boolean parseRemoteEndpoint(Object request, Endpoint.Builder remoteEndpoint) {
        remoteEndpoint.serviceName("romeo");
        return true;
      }
    };

    handler.handleStart(request, span);

    verify(span).remoteEndpoint(Endpoint.newBuilder().serviceName("romeo").build());
  }

  @Test public void handleFinish_nothingOnNoop_success() {
    when(span.isNoop()).thenReturn(true);

    handler.handleFinish(response, null, span);

    verify(span, never()).finish();
  }

  @Test public void handleFinish_nothingOnNoop_error() {
    when(span.isNoop()).thenReturn(true);

    handler.handleFinish(null, new RuntimeException("drat"), span);

    verify(span, never()).finish();
  }

  @Test public void handleFinish_parsesTagsWithCustomizer() {
    when(adapter.statusCodeAsInt(response)).thenReturn(404);
    when(span.customizer()).thenReturn(spanCustomizer);

    handler.handleFinish(response, null, span);

    verify(spanCustomizer).tag("http.status_code", "404");
    verify(spanCustomizer).tag("error", "404");
    verifyNoMoreInteractions(spanCustomizer);
  }

  @Test public void handleFinish_parsesTagsInScope() {
    handler = new HttpHandler(currentTraceContext, adapter, new HttpParser() {
      @Override public <Resp> void response(HttpAdapter<?, Resp> adapter, Resp res, Throwable error,
          SpanCustomizer customizer) {
        assertThat(currentTraceContext.get()).isNotNull();
      }
    }) {
      @Override boolean parseRemoteEndpoint(Object request, Endpoint.Builder remoteEndpoint) {
        return false;
      }
    };

    handler.handleFinish(response, null, span);
  }

  @Test public void handleFinish_finishesWhenSpanNotInScope() {
    doAnswer(new Answer() {
      @Override public Object answer(InvocationOnMock invocation) {
        assertThat(currentTraceContext.get()).isNull();
        return null;
      }
    }).when(span).finish();

    handler.handleFinish(response, null, span);
  }

  @Test public void handleFinish_finishesWhenSpanNotInScope_clearingIfNecessary() {
    try (CurrentTraceContext.Scope ws = currentTraceContext.newScope(context)) {
      handleFinish_finishesWhenSpanNotInScope();
    }
  }

  @Test public void handleFinish_finishedEvenIfAdapterThrows() {
    when(adapter.statusCodeAsInt(response)).thenThrow(new RuntimeException());

    try {
      handler.handleFinish(response, null, span);
      failBecauseExceptionWasNotThrown(RuntimeException.class);
    } catch (RuntimeException e) {
      verify(span).finish();
    }
  }
}
