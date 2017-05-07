package brave.servlet;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public final class TracingFilter implements Filter {
  public static Filter create(Tracing tracing) {
    return new TracingFilter(HttpTracing.create(tracing));
  }

  public static Filter create(HttpTracing httpTracing) {
    return new TracingFilter(httpTracing);
  }

  final ServletRuntime servlet = ServletRuntime.get();
  final Tracer tracer;
  final HttpServletHandler handler;
  final TraceContext.Extractor<HttpServletRequest> extractor;

  TracingFilter(HttpTracing httpTracing) {
    tracer = httpTracing.tracing().tracer();
    handler = new HttpServletHandler(httpTracing.serverParser());
    extractor = httpTracing.tracing().propagation().extractor(HttpServletRequest::getHeader);
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = servlet.httpResponse(response);

    Span span = tracer.nextSpan(extractor, httpRequest);
    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
      handler.handleReceive(httpRequest, span); // start the span

      chain.doFilter(httpRequest, httpResponse); // any downstream filters see Tracer.currentSpan

      if (servlet.isAsync(httpRequest)) { // we don't have the actual response, handle later
        servlet.handleAsync(handler, httpRequest, span);
      } else { // we have a synchronous response, so we can finish the span
        handler.handleSend(httpResponse, span);
      }
    } catch (IOException | ServletException | RuntimeException e) {
      handler.handleError(e, span);
      throw e;
    }
  }

  @Override public void destroy() {
  }

  @Override
  public void init(FilterConfig filterConfig) {
  }
}
