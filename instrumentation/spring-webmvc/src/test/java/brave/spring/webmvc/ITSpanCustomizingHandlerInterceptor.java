/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.webmvc;

import brave.test.http.Jetty9ServerController;
import java.util.EnumSet;
import javax.servlet.DispatcherType;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

class ITSpanCustomizingHandlerInterceptor extends BaseITSpanCustomizingHandlerInterceptor {
  public ITSpanCustomizingHandlerInterceptor() {
    super(new Jetty9ServerController());
  }

  @Override protected void addDelegatingTracingFilter(ServletContextHandler handler) {
    handler.addFilter(DelegatingTracingFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
  }

  @Override
  protected void registerTestController(AnnotationConfigWebApplicationContext appContext) {
    appContext.register(Servlet3TestController.class); // the test resource
  }
}
