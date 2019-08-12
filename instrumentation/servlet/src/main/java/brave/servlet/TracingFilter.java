/*
 * Copyright 2013-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.servlet;

import brave.Span;
import brave.SpanCustomizer;
import brave.Tracing;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.Propagation.Getter;
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
  static final Getter<HttpServletRequest, String> GETTER =
    new Getter<HttpServletRequest, String>() {
      @Override public String get(HttpServletRequest carrier, String key) {
        return carrier.getHeader(key);
      }

      @Override public String toString() {
        return "HttpServletRequest::getHeader";
      }
    };
  static final HttpServletAdapter ADAPTER = new HttpServletAdapter();

  public static Filter create(Tracing tracing) {
    return new TracingFilter(HttpTracing.create(tracing));
  }

  public static Filter create(HttpTracing httpTracing) {
    return new TracingFilter(httpTracing);
  }

  final ServletRuntime servlet = ServletRuntime.get();
  final CurrentTraceContext currentTraceContext;
  final HttpServerHandler<HttpServletRequest, HttpServletResponse> handler;
  final TraceContext.Extractor<HttpServletRequest> extractor;

  TracingFilter(HttpTracing httpTracing) {
    currentTraceContext = httpTracing.tracing().currentTraceContext();
    handler = HttpServerHandler.create(httpTracing, ADAPTER);
    extractor = httpTracing.tracing().propagation().extractor(GETTER);
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
    throws IOException, ServletException {
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = servlet.httpResponse(response);

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

    Span span = handler.handleReceive(extractor, httpRequest);

    // Add attributes for explicit access to customization or span context
    request.setAttribute(SpanCustomizer.class.getName(), span.customizer());
    request.setAttribute(TraceContext.class.getName(), span.context());

    Throwable error = null;
    Scope scope = currentTraceContext.newScope(span.context());
    try {
      // any downstream code can see Tracer.currentSpan() or use Tracer.currentSpanCustomizer()
      chain.doFilter(httpRequest, httpResponse);
    } catch (IOException | ServletException | RuntimeException | Error e) {
      error = e;
      throw e;
    } finally {
      scope.close();
      if (servlet.isAsync(httpRequest)) { // we don't have the actual response, handle later
        servlet.handleAsync(handler, httpRequest, httpResponse, span);
      } else { // we have a synchronous response, so we can finish the span
        handler.handleSend(ADAPTER.adaptResponse(httpRequest, httpResponse), error, span);
      }
    }
  }

  @Override public void destroy() {
  }

  @Override
  public void init(FilterConfig filterConfig) {
  }
}
