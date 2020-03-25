/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
package brave.test.http;

import brave.Tracing;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.AfterClass;
import org.junit.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class ITServlet3Container extends ITServlet25Container {
  static ExecutorService executor = Executors.newCachedThreadPool();

  public ITServlet3Container() {
    super(new Jetty9ServerController());
  }

  @AfterClass public static void shutdownExecutor() {
    executor.shutdownNow();
  }

  @Test public void forward() throws Exception {
    get("/forward");

    reporter.takeRemoteSpan(Span.Kind.SERVER);
  }

  @Test public void forwardAsync() throws Exception {
    get("/forwardAsync");

    reporter.takeRemoteSpan(Span.Kind.SERVER);
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
      throw new IllegalStateException("not ready");
    }
  }

  @Test public void errorTag_onException_asyncTimeout() throws Exception {
    Span span = httpStatusCodeTagMatchesResponse("/exceptionAsyncTimeout", "Timed out after 1ms");

    assertThat(span.tags())
      .containsEntry("http.status_code", "500"); // TODO: why is this not 504?
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

  @Test public void errorTag_onException_asyncDispatch() throws Exception {
    httpStatusCodeTagMatchesResponse("/exceptionAsyncDispatch", "not ready");
  }

  static class DispatchExceptionAsyncServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
      if (DispatcherType.ERROR.equals(req.getDispatcherType())) return; // don't loop

      if (req.getAttribute("dispatched") != null) {
        throw new IllegalStateException("not ready");
      }

      req.setAttribute("dispatched", Boolean.TRUE);
      req.startAsync().dispatch();
    }
  }

  @Override public void init(ServletContextHandler handler) {
    super.init(handler);
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
}
