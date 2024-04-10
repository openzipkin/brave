/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.webmvc;

import brave.test.http.ServletContainer;
import java.util.EnumSet;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.DispatcherType;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

class ITSpanCustomizingHandlerInterceptor extends BaseITSpanCustomizingHandlerInterceptor {
  public ITSpanCustomizingHandlerInterceptor() {
    super(new Jetty7ServerController());
  }

  /** Overridden to support Jetty 7.x (that uses Servlet 2.5) */
  static final class Jetty7ServerController implements ServletContainer.ServerController {
    @Override public Server newServer(int port) {
      Server result = new Server();
      SocketConnector connector = new SocketConnector();
      connector.setMaxIdleTime(1000 * 60 * 60);
      connector.setPort(port);
      result.setConnectors(new Connector[] {connector});
      return result;
    }

    @Override public int getLocalPort(Server server) {
      return server.getConnectors()[0].getLocalPort();
    }
  }

  @Override protected void addDelegatingTracingFilter(ServletContextHandler handler) {
    handler.addFilter(DelegatingTracingFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
  }

  @Override
  protected void registerTestController(AnnotationConfigWebApplicationContext appContext) {
    appContext.register(Servlet25TestController.class); // the test resource
  }
}
