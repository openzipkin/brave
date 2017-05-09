package com.github.kristofa.brave.http;

import com.github.kristofa.brave.Brave;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.After;

/** Starts a jetty server which runs a servlet container */
public abstract class ITServletContainer extends ITHttpServer {
  int port = 0; // initially get a port, later reuse one
  Server server;

  /** recreates the server so that tracing uses the supplied trace configuration */
  @Override protected final void init(Brave brave, SpanNameProvider spanNameProvider) {
    stop();
    SocketConnector connector = new SocketConnector();
    connector.setMaxIdleTime(1000 * 60 * 60);
    connector.setPort(port);
    server = new Server();
    server.setConnectors(new Connector[] {connector});

    ServletContextHandler context = new ServletContextHandler();
    context.setContextPath("/");
    server.setHandler(context);

    init(context, brave, spanNameProvider);

    try {
      server.start();
      port = server.getConnectors()[0].getLocalPort();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to start server.", e);
    }
  }

  @Override protected final String url(String path) {
    return "http://localhost:" + port + path;
  }

  /** Implement by registering a servlet for the test resource and anything needed for tracing */
  public abstract void init(ServletContextHandler handler, Brave brave,
      SpanNameProvider spanNameProvider);

  @After
  public void stop() {
    if (server == null) return;
    try {
      server.stop();
      server.join();
      server = null;
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
