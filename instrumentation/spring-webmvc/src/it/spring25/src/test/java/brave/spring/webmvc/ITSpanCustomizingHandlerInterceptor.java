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
package brave.spring.webmvc;

import brave.Tracer;
import brave.http.HttpTracing;
import brave.test.http.ITServletContainer;
import java.io.IOException;
import java.util.EnumSet;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.DispatcherType;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.AssumptionViolatedException;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.mvc.annotation.AnnotationMethodHandlerAdapter;
import org.springframework.web.servlet.mvc.annotation.DefaultAnnotationHandlerMapping;

import static org.springframework.web.servlet.DispatcherServlet.HANDLER_ADAPTER_BEAN_NAME;
import static org.springframework.web.servlet.DispatcherServlet.HANDLER_MAPPING_BEAN_NAME;

public class ITSpanCustomizingHandlerInterceptor extends ITServletContainer {

  @Override public void notFound() {
    throw new AssumptionViolatedException("TODO: add MVC handling for not found");
  }

  @Override public void httpRoute() {
    throw new AssumptionViolatedException(
      "HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE doesn't exist until Spring 3");
  }

  @Override public void httpRoute_nested() {
    throw new AssumptionViolatedException(
      "HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE doesn't exist until Spring 3");
  }

  @Controller
  public static class TestController {
    final Tracer tracer;

    @Autowired public TestController(HttpTracing httpTracing) {
      this.tracer = httpTracing.tracing().tracer();
    }

    @RequestMapping(value = "/foo")
    public void foo(HttpServletResponse response) throws IOException {
      response.getWriter().write("foo");
    }

    @RequestMapping(value = "/badrequest")
    public void badrequest(HttpServletResponse response) throws IOException {
      response.sendError(400);
      response.flushBuffer();
    }

    @RequestMapping(value = "/child")
    public void child() {
      tracer.nextSpan().name("child").start().finish();
    }

    @RequestMapping(value = "/exception")
    public void disconnect() throws IOException {
      throw new IOException();
    }
  }

  @Override public void init(ServletContextHandler handler) {
    StaticWebApplicationContext wac = new StaticWebApplicationContext();
    wac.getBeanFactory()
      .registerSingleton("httpTracing", httpTracing);

    wac.getBeanFactory()
      .registerSingleton("testController", new TestController(httpTracing)); // the test resource

    DefaultAnnotationHandlerMapping mapping = new DefaultAnnotationHandlerMapping();
    mapping.setInterceptors(new Object[] {new SpanCustomizingHandlerInterceptor()});
    mapping.setApplicationContext(wac);

    wac.getBeanFactory().registerSingleton(HANDLER_MAPPING_BEAN_NAME, mapping);
    wac.getBeanFactory()
      .registerSingleton(HANDLER_ADAPTER_BEAN_NAME, new AnnotationMethodHandlerAdapter());

    handler.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, wac);
    handler.addServlet(new ServletHolder(new DispatcherServlet() {
      {
        wac.refresh();
        setDetectAllHandlerMappings(false);
        setDetectAllHandlerAdapters(false);
        setPublishEvents(false);
      }

      @Override protected WebApplicationContext initWebApplicationContext() throws BeansException {
        onRefresh(wac);
        return wac;
      }
    }), "/*");
    handler.addFilter(DelegatingTracingFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
  }
}
