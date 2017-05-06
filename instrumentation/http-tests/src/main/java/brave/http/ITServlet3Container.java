package brave.http;

import java.io.IOException;
import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public abstract class ITServlet3Container extends ITServlet25Container {

  static class AsyncServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
      AsyncContext ctx = req.startAsync();
      ctx.start(ctx::complete);
    }
  }

  static class ExceptionAsyncServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      AsyncContext ctx = req.startAsync();
      ctx.setTimeout(1);
      ctx.start(() -> {
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
    handler.addServlet(new ServletHolder(new ExceptionAsyncServlet()), "/exceptionAsync");
  }
}
