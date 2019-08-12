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
package brave.sparkjava;

import brave.servlet.TracingFilter;
import brave.test.http.ITServletContainer;
import brave.test.http.ServletContainer;
import java.util.EnumSet;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration.Dynamic;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import spark.servlet.SparkFilter;

public class ITTracingFilter extends ITServletContainer {
  /** Override to support Jetty 9.x */
  @Override protected ServletContainer newServletContainer() {
    return new ServletContainer() {
      @Override protected Server newServer(int port) {
        Server result = new Server();
        ServerConnector connector = new ServerConnector(result);
        connector.setPort(port);
        connector.setIdleTimeout(1000 * 60 * 60);
        result.setConnectors(new Connector[] {connector});
        return result;
      }

      @Override protected int getLocalPort(Server server) {
        return ((ServerConnector) server.getConnectors()[0]).getLocalPort();
      }

      @Override public void init(ServletContextHandler handler) {
        ITTracingFilter.this.init(handler);
      }
    };
  }

  @Override public void init(ServletContextHandler handler) {
    handler.getServletContext()
      .addFilter("tracingFilter", TracingFilter.create(httpTracing))
      .addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");

    Dynamic sparkFilter = handler.getServletContext().addFilter("sparkFilter", new SparkFilter());
    sparkFilter.setInitParameter("applicationClass", TestApplication.class.getName());
    sparkFilter.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
  }
}
