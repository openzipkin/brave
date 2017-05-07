package brave.sparkjava;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import brave.servlet.HttpServletHandler;
import spark.ExceptionHandler;
import spark.Filter;
import spark.Request;

public final class SparkTracing {
  public static SparkTracing create(Tracing tracing) {
    return new SparkTracing(HttpTracing.create(tracing));
  }

  public static SparkTracing create(HttpTracing httpTracing) {
    return new SparkTracing(httpTracing);
  }

  final Tracer tracer;
  final HttpServletHandler handler;
  final TraceContext.Extractor<Request> extractor;

  SparkTracing(HttpTracing httpTracing) {
    tracer = httpTracing.tracing().tracer();
    handler = new HttpServletHandler(httpTracing.serverParser());
    extractor = httpTracing.tracing().propagation().extractor(Request::headers);
  }

  public Filter before() {
    return (request, response) -> {
      Span span = tracer.nextSpan(extractor, request);
      handler.handleReceive(request.raw(), span);
      request.attribute(Tracer.SpanInScope.class.getName(), tracer.withSpanInScope(span));
    };
  }

  public Filter afterAfter() {
    return (request, response) -> {
      Span span = tracer.currentSpan();
      if (span == null) return;
      ((Tracer.SpanInScope) request.attribute(Tracer.SpanInScope.class.getName())).close();
      handler.handleSend(response.raw(), span);
    };
  }

  public ExceptionHandler exception(ExceptionHandler delegate) {
    return (exception, request, response) -> {
      Span span = tracer.currentSpan();
      if (span != null) handler.handleError(exception, span);
      delegate.handle(exception, request, response);
    };
  }
}
