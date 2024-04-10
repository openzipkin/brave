/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.test.http;

import brave.test.http.ServletContainer.ServerController;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.junit.jupiter.api.AfterEach;

/** Starts a jetty server which runs a servlet container */
public abstract class ITServletContainer extends ITHttpServer {
  final ServerController serverController;

  protected ITServletContainer(ServerController serverController) {
      this(serverController, new Log4J2Log());
  }
  
  protected ITServletContainer(ServerController serverController, Logger logger) {
    Log.setLog(logger);
    this.serverController = serverController;
  }

  ServletContainer container;

  protected ServletContainer newServletContainer() {
    return new ServletContainer(serverController) {
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

  @AfterEach
  public void stop() {
    container.stop();
  }
}
