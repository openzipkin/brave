package brave.test.http;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.After;

/** Starts a jetty server which runs a servlet container */
public abstract class ITServletContainer extends ITHttpServer {
  ServletContainer container;

  protected ServletContainer newServletContainer() {
    return new ServletContainer() {
      @Override public void init(ServletContextHandler handler) {
        ITServletContainer.this.init(handler);
      }
    };
  }

  /** recreates the server so that it uses the supplied trace configuration */
  @Override protected final void init() {
    container = newServletContainer();
    container.init();
  }

  @Override protected final String url(String path) {
    return container.url(path);
  }

  /** Implement by registering a servlet for the test resource and anything needed for tracing */
  public abstract void init(ServletContextHandler handler);

  @After
  public void stop() {
    container.stop();
  }
}
