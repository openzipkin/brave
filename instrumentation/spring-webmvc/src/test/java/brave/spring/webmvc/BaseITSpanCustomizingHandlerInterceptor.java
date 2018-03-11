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
        .containsEntry("mvc.controller.class", "TestController")
        .containsEntry("mvc.controller.method", "foo");
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

    appContext.register(TestController.class); // the test resource
    appContext.register(TracingConfig.class); // generic tracing setup
    DispatcherServlet servlet = new DispatcherServlet(appContext);
    servlet.setDispatchOptionsRequest(true);
    handler.addServlet(new ServletHolder(servlet), "/*");
    handler.addEventListener(new ContextLoaderListener(appContext));

    // add the trace filter, which lazy initializes a real tracing filter from the spring context
    addDelegatingTracingFilter(handler);
  }

  // abstract because filter registration types were not introduced until servlet 3.0
  protected abstract void addDelegatingTracingFilter(ServletContextHandler handler);
}
