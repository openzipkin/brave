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
package brave.test.http;

import brave.Tracing;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.AfterClass;
import org.junit.Test;

public abstract class ITServlet3Container extends ITServlet25Container {
  static ExecutorService executor = Executors.newCachedThreadPool();

  @AfterClass
  public static void shutdownExecutor() {
    executor.shutdownNow();
  }

  static class AsyncServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
      if (Tracing.currentTracer().currentSpan() == null) {
        throw new IllegalStateException("couldn't read current span!");
      }
      AsyncContext ctx = req.startAsync();
      ctx.start(ctx::complete);
    }
  }

  @Test
  public void forward() throws Exception {
    get("/forward");

    takeSpan();
  }

  @Test
  public void forwardAsync() throws Exception {
    get("/forwardAsync");

    takeSpan();
  }

  static class ForwardServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
      req.getServletContext().getRequestDispatcher("/foo").forward(req, resp);
    }
  }

  static class AsyncForwardServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
      AsyncContext asyncContext = req.startAsync(req, resp);
      executor.execute(() -> asyncContext.dispatch("/async"));
    }
  }

  static class ExceptionAsyncServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
      AsyncContext ctx = req.startAsync();
      ctx.setTimeout(1);
      ctx.start(
        () -> {
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

  @Override
  public void init(ServletContextHandler handler) {
    super.init(handler);
    // add servlet 3.0+
    handler.addServlet(new ServletHolder(new AsyncServlet()), "/async");
    handler.addServlet(new ServletHolder(new ForwardServlet()), "/forward");
    handler.addServlet(new ServletHolder(new AsyncForwardServlet()), "/forwardAsync");
    handler.addServlet(new ServletHolder(new ExceptionAsyncServlet()), "/exceptionAsync");
  }
}
