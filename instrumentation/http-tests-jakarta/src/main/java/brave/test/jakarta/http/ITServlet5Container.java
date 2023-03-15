/*
 * Copyright 2013-2022 The OpenZipkin Authors
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
package brave.test.jakarta.http;

import brave.Tracer;
import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import brave.test.http.ITServletContainer;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.log.Log;
import org.junit.AfterClass;
import org.junit.Test;

import static brave.Span.Kind.SERVER;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class ITServlet5Container extends ITServletContainer {
  static ExecutorService executor = Executors.newCachedThreadPool();

  public ITServlet5Container() {
    super(new Jetty11ServerController(), Log.getLogger("org.eclipse.jetty.util.log"));
  }

  @AfterClass public static void shutdownExecutor() {
    executor.shutdownNow();
  }

  @Test public void forward() throws Exception {
    get("/forward");

    testSpanHandler.takeRemoteSpan(SERVER);
  }

  @Test public void forwardAsync() throws Exception {
    get("/forwardAsync");

    testSpanHandler.takeRemoteSpan(SERVER);
  }

  Filter delegate;

  class DelegatingFilter implements Filter {

    @Override public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
      if (delegate == null) {
        chain.doFilter(request, response);
      } else {
        delegate.doFilter(request, response, chain);
      }
    }

    @Override public void destroy() {
    }
  }
  
  static class StatusServlet extends HttpServlet {
    final int status;

    StatusServlet(int status) {
      this.status = status;
    }

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
      resp.setStatus(status);
    }
  }

  static class BaggageServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
      resp.getWriter().print(BAGGAGE_FIELD.getValue());
    }
  }

  static class ChildServlet extends HttpServlet {
    final Tracer tracer;

    ChildServlet(HttpTracing httpTracing) {
      this.tracer = httpTracing.tracing().tracer();
    }

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
      tracer.nextSpan().name("child").start().finish();
      resp.setStatus(200);
    }
  }

  static class ExceptionServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
      // Change the status from 500 to 503
      req.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 503);
      throw NOT_READY_ISE;
    }
  }
    
  static class ForwardServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
      req.getServletContext().getRequestDispatcher("/foo").forward(req, resp);
    }
  }

  static class AsyncForwardServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
      AsyncContext asyncContext = req.startAsync(req, resp);
      executor.execute(() -> asyncContext.dispatch("/async"));
    }
  }

  static class AsyncServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
      if (Tracing.currentTracer().currentSpan() == null) {
        throw new IllegalStateException("couldn't read current span!");
      }
      AsyncContext ctx = req.startAsync();
      ctx.start(ctx::complete);
    }
  }

  static class ExceptionAsyncServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
      if (DispatcherType.ERROR.equals(req.getDispatcherType())) return; // don't loop

      AsyncContext async = req.startAsync();
      // unless we add a listener, the onError hook will never occur
      async.addListener(new AsyncListener() {
        @Override public void onComplete(AsyncEvent event) {
        }

        @Override public void onTimeout(AsyncEvent event) {
        }

        @Override public void onError(AsyncEvent event) {
          // Change the status from 500 to 503
          req.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 503);
        }

        @Override public void onStartAsync(AsyncEvent event) {
        }
      });
      throw NOT_READY_ISE;
    }
  }

  @Test public void errorTag_onException_asyncTimeout() throws Exception {
    Response response =
        httpStatusCodeTagMatchesResponse_onUncaughtException("/exceptionAsyncTimeout", "Timed out after 1ms");

    assertThat(response.code()).isIn(500, 504); // Jetty is inconsistent
  }

  static class TimeoutExceptionAsyncServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
      if (DispatcherType.ERROR.equals(req.getDispatcherType())) return; // don't loop

      AsyncContext ctx = req.startAsync();
      ctx.setTimeout(1 /* ms */);
      ctx.start(
        () -> {
          resp.setStatus(504);
          try {
            Thread.sleep(10L);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            ctx.complete();
          }
        });
    }
  }

  //copies the header to the response
  Filter userFilter = new Filter() {
    @Override public void init(FilterConfig filterConfig) {
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
      ((HttpServletResponse) response).setHeader(BAGGAGE_FIELD_KEY, BAGGAGE_FIELD.getValue());
      chain.doFilter(request, response);
    }
    
    @Override public void destroy() {
    }
  };
    
  @Test public void currentSpanVisibleToOtherFilters() throws Exception {
    delegate = userFilter;
    
    String path = "/foo";
    
    Request request = new Request.Builder().url(url(path))
      .header(BAGGAGE_FIELD_KEY, "abcdefg").build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
      assertThat(response.header(BAGGAGE_FIELD_KEY))
        .isEqualTo("abcdefg");
    }
    
    testSpanHandler.takeRemoteSpan(SERVER);
    }
    
    // copies the header to the response
    Filter traceContextFilter = new Filter() {
    @Override public void init(FilterConfig filterConfig) {
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
      TraceContext context = (TraceContext) request.getAttribute("brave.propagation.TraceContext");
      String value = BAGGAGE_FIELD.getValue(context);
        ((HttpServletResponse) response).setHeader(BAGGAGE_FIELD_KEY, value);
        chain.doFilter(request, response);
    }
    
    @Override public void destroy() {
    }
  };
    
  @Test public void traceContextVisibleToOtherFilters() throws Exception {
    delegate = traceContextFilter;
    
    String path = "/foo";
    
    Request request = new Request.Builder().url(url(path))
      .header(BAGGAGE_FIELD_KEY, "abcdefg").build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
      assertThat(response.header(BAGGAGE_FIELD_KEY))
         .isEqualTo("abcdefg");
    }
    
    testSpanHandler.takeRemoteSpan(SERVER);
  }
  
  // Shows how a framework can layer on "http.route" logic
  Filter customHttpRoute = new Filter() {
    @Override public void init(FilterConfig filterConfig) {
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
      request.setAttribute("http.route", ((HttpServletRequest) request).getRequestURI());
      chain.doFilter(request, response);
    }
    
    @Override public void destroy() {
    }
  };
    
  /**
   * Shows that by adding the request attribute "http.route" a layered framework can influence any
   * derived from the route, including the span name.
   */
  @Test public void canSetCustomRoute() throws Exception {
    delegate = customHttpRoute;
    
    get("/foo");
    
    assertThat(testSpanHandler.takeRemoteSpan(SERVER).name())
      .isEqualTo("GET /foo");
  }
  
  Filter customHook = new Filter() {
    @Override public void init(FilterConfig filterConfig) {
  }
  
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
    throws IOException, ServletException {
      ((brave.SpanCustomizer) request.getAttribute("brave.SpanCustomizer")).tag("foo", "bar");
      chain.doFilter(request, response);
    }
    
    @Override public void destroy() {
    }
  };
    
  /**
   * Shows that a framework can directly use the "brave.Span" rather than relying on the current
   * span.
   */
  @Test public void canUseSpanAttribute() throws Exception {
    delegate = customHook;
  
    get("/foo");
  
    assertThat(testSpanHandler.takeRemoteSpan(SERVER).tags())
      .containsEntry("foo", "bar");
  }
   
  @Test public void errorTag_onException_asyncDispatch() throws Exception {
    httpStatusCodeTagMatchesResponse_onUncaughtException("/exceptionAsyncDispatch", "not ready");
  }

  static class DispatchExceptionAsyncServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
      if (DispatcherType.ERROR.equals(req.getDispatcherType())) return; // don't loop

      if (req.getAttribute("dispatched") != null) {
        throw NOT_READY_ISE;
      }

      req.setAttribute("dispatched", Boolean.TRUE);
      req.startAsync().dispatch();
    }
  }

  @Override public void init(ServletContextHandler handler) {
    // add servlets for the test resource
    handler.addServlet(new ServletHolder(new StatusServlet(404)), "/*");
    handler.addServlet(new ServletHolder(new StatusServlet(200)), "/foo");
    handler.addServlet(new ServletHolder(new BaggageServlet()), "/baggage");
    handler.addServlet(new ServletHolder(new StatusServlet(400)), "/badrequest");
    handler.addServlet(new ServletHolder(new ChildServlet(httpTracing)), "/child");
    handler.addServlet(new ServletHolder(new ExceptionServlet()), "/exception");

    // add the trace filter
    addFilter(handler, newTracingFilter());
    // add a holder for test filters
    addFilter(handler, new DelegatingFilter());

    // add servlet 3.0+
    handler.addServlet(new ServletHolder(new AsyncServlet()), "/async");
    handler.addServlet(new ServletHolder(new ForwardServlet()), "/forward");
    handler.addServlet(new ServletHolder(new AsyncForwardServlet()), "/forwardAsync");
    handler.addServlet(new ServletHolder(new ExceptionAsyncServlet()), "/exceptionAsync");
    handler.addServlet(new ServletHolder(new TimeoutExceptionAsyncServlet()),
      "/exceptionAsyncTimeout");
    handler.addServlet(new ServletHolder(new DispatchExceptionAsyncServlet()),
      "/exceptionAsyncDispatch");
  }
  
  protected abstract Filter newTracingFilter();

  // abstract because filter registration types were not introduced until servlet 3.0
  protected abstract void addFilter(ServletContextHandler handler, Filter filter);

}
