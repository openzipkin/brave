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

import brave.test.http.ITServletContainer;
import java.util.EnumSet;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration.Dynamic;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

/** This tests when you use servlet for tracing but MVC for tagging */
public class ITSpanCustomizingAsyncHandlerInterceptor extends ITServletContainer {

  @Test public void addsControllerTags() throws Exception {
    get("/foo");

    Span span = takeSpan();
    assertThat(span.tags())
      .containsEntry("mvc.controller.class", "Servlet3TestController")
      .containsEntry("mvc.controller.method", "foo");
  }

  @Configuration
  @EnableWebMvc
  static class TracingConfig extends WebMvcConfigurerAdapter {
    @Override public void addInterceptors(InterceptorRegistry registry) {
      registry.addInterceptor(new SpanCustomizingAsyncHandlerInterceptor());
    }
  }

  @Override public void init(ServletContextHandler handler) {
    AnnotationConfigWebApplicationContext appContext =
      new AnnotationConfigWebApplicationContext() {
        // overriding this allows us to register dependencies of TracingHandlerInterceptor
        // without passing static state to a configuration class.
        @Override protected void loadBeanDefinitions(DefaultListableBeanFactory beanFactory) {
          beanFactory.registerSingleton("httpTracing", httpTracing);
          super.loadBeanDefinitions(beanFactory);
        }
      };

    appContext.register(Servlet3TestController.class); // the test resource
    appContext.register(TracingConfig.class); // generic tracing setup
    DispatcherServlet servlet = new DispatcherServlet(appContext);
    servlet.setDispatchOptionsRequest(true);
    ServletHolder servletHolder = new ServletHolder(servlet);
    servletHolder.setAsyncSupported(true);
    handler.addServlet(servletHolder, "/*");
    handler.addEventListener(new ContextLoaderListener(appContext));

    // add the trace filter, which lazy initializes a real tracing filter from the spring context
    Dynamic filterRegistration =
      handler.getServletContext().addFilter("tracingFilter", DelegatingTracingFilter.class);
    filterRegistration.setAsyncSupported(true);
    filterRegistration.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
  }
}
