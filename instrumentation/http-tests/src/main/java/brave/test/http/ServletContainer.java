/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.test.http;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;

/** Starts a jetty server which runs a servlet container */
public abstract class ServletContainer {
  final ServerController serverController;
  int port = 0; // initially get a port, later reuse one
  Server server;

  protected ServletContainer(ServerController serverController) {
    this.serverController = serverController;
  }

  /** recreates the server so that it uses the supplied trace configuration */
  public final void init() {
    stop();
    server = serverController.newServer(port);

    ServletContextHandler context = new ServletContextHandler();
    context.setContextPath("/");
    server.setHandler(context);

    init(context);

    try {
      server.start();
      port = serverController.getLocalPort(server);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to start server.", e);
    }
  }

  /** Allows us to work with multiple Jetty versions. */
  public interface ServerController {
    Server newServer(int port);

    int getLocalPort(Server server);
  }

  public final String url(String path) {
    return "http://localhost:" + port + path;
  }

  /** Implement by registering a servlet for the test resource and anything needed for tracing */
  public abstract void init(ServletContextHandler handler);

  public void stop() {
    if (server == null) return;
    try {
      server.stop();
      server.join();
      server = null;
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }
}
