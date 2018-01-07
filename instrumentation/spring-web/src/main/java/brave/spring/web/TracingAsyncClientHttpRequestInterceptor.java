package brave.spring.web;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import brave.spring.web.TracingClientHttpRequestInterceptor.HttpAdapter;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.AsyncClientHttpRequestExecution;
import org.springframework.http.client.AsyncClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import static brave.spring.web.TracingClientHttpRequestInterceptor.SETTER;

public final class TracingAsyncClientHttpRequestInterceptor
    implements AsyncClientHttpRequestInterceptor {

  public static AsyncClientHttpRequestInterceptor create(Tracing tracing) {
    return create(HttpTracing.create(tracing));
  }

  public static AsyncClientHttpRequestInterceptor create(HttpTracing httpTracing) {
    return new TracingAsyncClientHttpRequestInterceptor(httpTracing);
  }

  final Tracer tracer;
  final HttpClientHandler<HttpRequest, ClientHttpResponse> handler;
  final TraceContext.Injector<HttpHeaders> injector;

  @Autowired TracingAsyncClientHttpRequestInterceptor(HttpTracing httpTracing) {
    tracer = httpTracing.tracing().tracer();
    handler = HttpClientHandler.create(httpTracing, new HttpAdapter());
    injector = httpTracing.tracing().propagation().injector(SETTER);
  }

  @Override public ListenableFuture<ClientHttpResponse> intercept(HttpRequest request,
      byte[] body, AsyncClientHttpRequestExecution execution) throws IOException {
    Span span = handler.handleSend(injector, request.getHeaders(), request);
    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
      ListenableFuture<ClientHttpResponse> result = execution.executeAsync(request, body);
      result.addCallback(new TraceListenableFutureCallback(span, handler));
      return result;
    } catch (IOException | RuntimeException | Error e) {
      handler.handleReceive(null, e, span);
      throw e;
    }
  }

  static final class TraceListenableFutureCallback
      implements ListenableFutureCallback<ClientHttpResponse> {
    final Span span;
    final HttpClientHandler<HttpRequest, ClientHttpResponse> handler;

    TraceListenableFutureCallback(Span span,
        HttpClientHandler<HttpRequest, ClientHttpResponse> handler) {
      this.span = span;
      this.handler = handler;
    }

    @Override public void onFailure(Throwable ex) {
      handler.handleReceive(null, ex, span);
    }

    @Override public void onSuccess(ClientHttpResponse result) {
      handler.handleReceive(result, null, span);
    }
  }
}