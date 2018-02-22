package brave.spring.webmvc;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.Tracing;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.servlet.HttpServletAdapter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/** Tracing interceptor for Spring Web MVC {@link HandlerInterceptor}. */
public final class TracingHandlerInterceptor implements HandlerInterceptor {
  // redefined from HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE as doesn't exist until Spring 3
  static final String BEST_MATCHING_PATTERN_ATTRIBUTE =
      "org.springframework.web.servlet.HandlerMapping.bestMatchingPattern";
  static final Propagation.Getter<HttpServletRequest, String> GETTER =
      new Propagation.Getter<HttpServletRequest, String>() {
        @Override public String get(HttpServletRequest carrier, String key) {
          return carrier.getHeader(key);
        }

        @Override public String toString() {
          return "HttpServletRequest::getHeader";
        }
      };

  public static HandlerInterceptor create(Tracing tracing) {
    return new TracingHandlerInterceptor(HttpTracing.create(tracing));
  }

  public static HandlerInterceptor create(HttpTracing httpTracing) {
    return new TracingHandlerInterceptor(httpTracing);
  }

  final Tracer tracer;
  final HttpServerHandler<HttpServletRequest, HttpServletResponse> handler;
  final TraceContext.Extractor<HttpServletRequest> extractor;

  @Autowired TracingHandlerInterceptor(HttpTracing httpTracing) { // internal
    tracer = httpTracing.tracing().tracer();
    handler = HttpServerHandler.create(httpTracing, new HttpServletAdapter() {
      @Override public String route(HttpServletResponse response) {
        return response instanceof HttpServletResponseWithTemplate
            ? ((HttpServletResponseWithTemplate) response).template : null;
      }

      @Override public String toString() {
        return "WebMVCAdapter{}";
      }
    });
    extractor = httpTracing.tracing().propagation().extractor(GETTER);
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
  public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
      ModelAndView modelAndView) {
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
      Object o, Exception ex) {
    Span span = tracer.currentSpan();
    if (span == null) return;
    ((SpanInScope) request.getAttribute(SpanInScope.class.getName())).close();
    Object template = request.getAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (template != null) {
      response = new HttpServletResponseWithTemplate(response, template.toString());
    }
    handler.handleSend(response, ex, span);
  }

  static class HttpServletResponseWithTemplate extends HttpServletResponseWrapper {
    final String template;

    HttpServletResponseWithTemplate(HttpServletResponse response, String template) {
      super(response);
      this.template = template;
    }
  }
}
