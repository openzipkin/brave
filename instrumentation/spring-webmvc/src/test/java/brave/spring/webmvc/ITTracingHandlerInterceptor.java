package brave.spring.webmvc;

import brave.Tracer;
import brave.http.HttpTracing;
import brave.http.ITServletContainer;
import java.io.IOException;
import java.util.concurrent.Callable;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

public class ITTracingHandlerInterceptor extends ITServletContainer {

  @Controller static class TestController {
    final Tracer tracer;

    @Autowired TestController(HttpTracing httpTracing) {
      this.tracer = httpTracing.tracing().tracer();
    }

    @RequestMapping(value = "/foo")
    public ResponseEntity<Void> foo() throws IOException {
      return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/badrequest")
    public ResponseEntity<Void> badrequest() throws IOException {
      return ResponseEntity.badRequest().build();
    }

    @RequestMapping(value = "/child")
    public ResponseEntity<Void> child() {
      tracer.nextSpan().name("child").start().finish();
      return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/async")
    public Callable<ResponseEntity<Void>> async() throws IOException {
      return () -> ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/exception")
    public ResponseEntity<Void> disconnect() throws IOException {
      throw new IOException();
    }

    @RequestMapping(value = "/exceptionAsync")
    public Callable<ResponseEntity<Void>> disconnectAsync() throws IOException {
      return () -> {
        throw new IOException();
      };
    }
  }

  @Configuration
  @EnableWebMvc
  @Import(TracingHandlerInterceptor.class)
  static class TracingConfig extends WebMvcConfigurerAdapter {

    @Autowired
    private TracingHandlerInterceptor interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
      registry.addInterceptor(interceptor).addPathPatterns();
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

    appContext.register(TestController.class); // the test resource
    appContext.register(TracingConfig.class); // generic tracing setup
    handler.addServlet(new ServletHolder(new DispatcherServlet(appContext)), "/*");
  }
}
