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
package brave.jaxrs2;

import brave.servlet.TracingFilter;
import brave.test.http.ITServletContainer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.ws.rs.core.Application;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.jboss.resteasy.plugins.server.servlet.ListenerBootstrap;
import org.jboss.resteasy.plugins.server.servlet.ResteasyBootstrap;
import org.jboss.resteasy.spi.ResteasyConfiguration;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.junit.AssumptionViolatedException;
import org.junit.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public class ITSpanCustomizingContainerFilter extends ITServletContainer {

  @Override @Test public void reportsClientAddress() {
    throw new AssumptionViolatedException("ContainerRequestContext doesn't include remote address");
  }

  @Test public void tagsResource() throws Exception {
    get("/foo");

    Span span = takeSpan();
    assertThat(span.tags())
      .containsEntry("jaxrs.resource.class", "TestResource")
      .containsEntry("jaxrs.resource.method", "foo");
  }

  @Override public void init(ServletContextHandler handler) {
    // Adds application programmatically as opposed to using web.xml
    handler.addServlet(new ServletHolder(new HttpServletDispatcher()), "/*");
    handler.addEventListener(new TaggingBootstrap(new TestResource(httpTracing)));

    addFilter(handler, TracingFilter.create(httpTracing));
  }

  void addFilter(ServletContextHandler handler, Filter filter) {
    handler.addFilter(new FilterHolder(filter), "/*", EnumSet.allOf(DispatcherType.class));
  }

  static class TaggingBootstrap extends ResteasyBootstrap {

    TaggingBootstrap(Object resource) {
      deployment = new ResteasyDeployment();
      deployment.setApplication(new Application() {
        @Override public Set<Object> getSingletons() {
          return new LinkedHashSet<>(Arrays.asList(
            resource,
            SpanCustomizingContainerFilter.create()
          ));
        }
      });
    }

    @Override public void contextInitialized(ServletContextEvent event) {
      ServletContext servletContext = event.getServletContext();
      ListenerBootstrap config = new ListenerBootstrap(servletContext);
      servletContext.setAttribute(ResteasyDeployment.class.getName(), deployment);
      deployment.getDefaultContextObjects().put(ResteasyConfiguration.class, config);
      config.createDeployment();
      deployment.start();
    }
  }
}
