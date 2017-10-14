package brave.http.features;

import brave.Span;
import brave.Tracer;
import brave.http.HttpServerAdapter;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import javax.annotation.Nonnull;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

final class TracingDispatcher extends Dispatcher {
  final Dispatcher delegate;
  final Tracer tracer;
  final HttpServerHandler<RecordedRequest, MockResponse> handler;
  final TraceContext.Extractor<RecordedRequest> extractor;

  TracingDispatcher(HttpTracing httpTracing, Dispatcher delegate) {
    tracer = httpTracing.tracing().tracer();
    handler = HttpServerHandler.create(httpTracing, new MockWebServerAdapter());
    extractor = httpTracing.tracing().propagation().extractor(RecordedRequest::getHeader);
    this.delegate = delegate;
  }

  @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
    Span span = handler.handleReceive(extractor, request);
    MockResponse response = null;
    Throwable error = null;
    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
      return response = delegate.dispatch(request);
    } catch (InterruptedException | RuntimeException | Error e) {
      error = e;
      throw e;
    } finally {
      handler.handleSend(response, error, span);
    }
  }

  static final class MockWebServerAdapter extends HttpServerAdapter<RecordedRequest, MockResponse> {

    @Override @Nonnull public String method(RecordedRequest request) {
      return request.getMethod();
    }

    @Override @Nonnull public String path(RecordedRequest request) {
      return request.getPath();
    }

    @Override @Nonnull public String url(RecordedRequest request) {
      return request.getRequestUrl().toString();
    }

    @Override @Nonnull public String requestHeader(RecordedRequest request, String name) {
      return request.getHeader(name);
    }

    @Override @Nonnull public Integer statusCode(MockResponse response) {
      return Integer.parseInt(response.getStatus().split(" ")[1]);
    }
  }
}
