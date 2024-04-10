/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jakarta.servlet;

import brave.Span;
import brave.SpanCustomizer;
import brave.Tracing;
import brave.http.HttpServerHandler;
import brave.http.HttpServerRequest;
import brave.http.HttpServerResponse;
import brave.http.HttpTracing;
import brave.jakarta.servlet.internal.ServletRuntime;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static brave.internal.Throwables.propagateIfFatal;

public final class TracingFilter implements Filter {
  public static Filter create(Tracing tracing) {
    return new TracingFilter(HttpTracing.create(tracing));
  }

  public static Filter create(HttpTracing httpTracing) {
    return new TracingFilter(httpTracing);
  }

  final ServletRuntime servlet = ServletRuntime.get();
  final CurrentTraceContext currentTraceContext;
  final HttpServerHandler<HttpServerRequest, HttpServerResponse> handler;

  TracingFilter(HttpTracing httpTracing) {
    currentTraceContext = httpTracing.tracing().currentTraceContext();
    handler = HttpServerHandler.create(httpTracing);
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
    throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse res = servlet.httpServletResponse(response);

    // Prevent duplicate spans for the same request
    TraceContext context = (TraceContext) request.getAttribute(TraceContext.class.getName());
    if (context != null) {
      // A forwarded request might end up on another thread, so make sure it is scoped
      Scope scope = currentTraceContext.maybeScope(context);
      try {
        chain.doFilter(request, response);
      } finally {
        scope.close();
      }
      return;
    }

    Span span = handler.handleReceive(new HttpServletRequestWrapper(req));

    // Add attributes for explicit access to customization or span context
    request.setAttribute(SpanCustomizer.class.getName(), span.customizer());
    request.setAttribute(TraceContext.class.getName(), span.context());
    SendHandled sendHandled = new SendHandled();
    request.setAttribute(SendHandled.class.getName(), sendHandled);

    Throwable error = null;
    Scope scope = currentTraceContext.newScope(span.context());
    try {
      // any downstream code can see Tracer.currentSpan() or use Tracer.currentSpanCustomizer()
      chain.doFilter(req, res);
    } catch (Throwable e) {
      propagateIfFatal(e);
      error = e;
      throw e;
    } finally {
      // When async, even if we caught an exception, we don't have the final response: defer
      if (servlet.isAsync(req)) {
        servlet.handleAsync(handler, req, res, span);
      } else if (sendHandled.compareAndSet(false, true)){
        // we have a synchronous response or error: finish the span
        HttpServerResponse responseWrapper = HttpServletResponseWrapper.create(req, res, error);
        handler.handleSend(responseWrapper, span);
      }
      scope.close();
    }
  }

  // Special type used to ensure handleSend is only called once
  static final class SendHandled extends AtomicBoolean {
  }

  @Override public void destroy() {
  }

  @Override public void init(FilterConfig filterConfig) {
  }
}
