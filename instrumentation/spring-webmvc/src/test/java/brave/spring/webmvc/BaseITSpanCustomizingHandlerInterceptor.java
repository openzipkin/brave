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

import brave.servlet.TracingFilter;
import brave.test.http.ITServletContainer;
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
public abstract class BaseITSpanCustomizingHandlerInterceptor extends ITServletContainer {

  @Test public void addsControllerTags() throws Exception {
    get("/foo");

    Span span = takeSpan();
    assertThat(span.tags())
      .containsKeys("mvc.controller.class", "mvc.controller.method");
    assertThat(span.tags().get("mvc.controller.class"))
      .endsWith("TestController"); // controller has a version prefix
    assertThat(span.tags().get("mvc.controller.method"))
      .isEqualTo("foo");
  }

  @Configuration
  @EnableWebMvc
  static class TracingConfig extends WebMvcConfigurerAdapter {
    @Override public void addInterceptors(InterceptorRegistry registry) {
      registry.addInterceptor(new SpanCustomizingHandlerInterceptor());
    }
  }

  @Override public void init(ServletContextHandler handler) {
    AnnotationConfigWebApplicationContext appContext =
      new AnnotationConfigWebApplicationContext() {
        // overriding this allows us to register dependencies of TracingHandlerInterceptor
        // without passing static state to a configuration class.
        @Override protected void loadBeanDefinitions(DefaultListableBeanFactory beanFactory) {
          beanFactory.registerSingleton("httpTracing", httpTracing);
          beanFactory.registerSingleton("tracingFilter", TracingFilter.create(httpTracing));
          super.loadBeanDefinitions(beanFactory);
        }
      };

    registerTestController(appContext);
    appContext.register(TracingConfig.class); // generic tracing setup
    DispatcherServlet servlet = new DispatcherServlet(appContext);
    servlet.setDispatchOptionsRequest(true);
    handler.addServlet(new ServletHolder(servlet), "/*");
    handler.addEventListener(new ContextLoaderListener(appContext));

    // add the trace filter, which lazy initializes a real tracing filter from the spring context
    addDelegatingTracingFilter(handler);
  }

  protected abstract void registerTestController(AnnotationConfigWebApplicationContext appContext);

  // abstract because filter registration types were not introduced until servlet 3.0
  protected abstract void addDelegatingTracingFilter(ServletContextHandler handler);
}
