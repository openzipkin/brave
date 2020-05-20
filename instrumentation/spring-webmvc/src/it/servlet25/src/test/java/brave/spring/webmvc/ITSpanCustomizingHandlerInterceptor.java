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
package brave.spring.webmvc;

import brave.test.http.ServletContainer;
import java.util.EnumSet;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.DispatcherType;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

public class ITSpanCustomizingHandlerInterceptor extends BaseITSpanCustomizingHandlerInterceptor {
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
