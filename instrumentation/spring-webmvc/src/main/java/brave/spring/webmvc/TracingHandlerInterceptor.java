package brave.spring.webmvc;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.Tracing;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import brave.servlet.HttpServletAdapter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

@Configuration // not final because of @Configuration
public class TracingHandlerInterceptor extends HandlerInterceptorAdapter {

  public static HandlerInterceptorAdapter create(Tracing tracing) {
    return new TracingHandlerInterceptor(HttpTracing.create(tracing));
  }

  public static HandlerInterceptorAdapter create(HttpTracing httpTracing) {
    return new TracingHandlerInterceptor(httpTracing);
  }

  final Tracer tracer;
  final HttpServerHandler<HttpServletRequest, HttpServletResponse> handler;
  final TraceContext.Extractor<HttpServletRequest> extractor;

  @Autowired TracingHandlerInterceptor(HttpTracing httpTracing) { // internal
    tracer = httpTracing.tracing().tracer();
    handler = HttpServerHandler.create(httpTracing, new HttpServletAdapter());
    extractor = httpTracing.tracing().propagation().extractor(HttpServletRequest::getHeader);
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object o) {
    if (request.getAttribute(SpanInScope.class.getName()) != null) {
      return true; // already handled (possibly due to async request)
    }

    Span span = handler.handleReceive(extractor, request);
    request.setAttribute(SpanInScope.class.getName(), tracer.withSpanInScope(span));
    return true;
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
      Object o, Exception ex) {
    Span span = tracer.currentSpan();
    if (span == null) return;
    ((SpanInScope) request.getAttribute(SpanInScope.class.getName())).close();
    handler.handleSend(response, ex, span);
  }
}
