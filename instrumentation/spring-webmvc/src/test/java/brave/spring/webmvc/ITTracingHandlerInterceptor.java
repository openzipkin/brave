package brave.spring.webmvc;

import brave.Tracer;
import brave.http.HttpTracing;
import brave.http.ITServletContainer;
import brave.propagation.ExtraFieldPropagation;
import java.io.IOException;
import java.util.concurrent.Callable;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerInterceptor;
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
      return new ResponseEntity<>(HttpStatus.OK);
    }

    @RequestMapping(value = "/extra")
    public ResponseEntity<String> extra() throws IOException {
      return new ResponseEntity<>(ExtraFieldPropagation.current(EXTRA_KEY), HttpStatus.OK);
    }

    @RequestMapping(value = "/badrequest")
    public ResponseEntity<Void> badrequest() throws IOException {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    @RequestMapping(value = "/child")
    public ResponseEntity<Void> child() {
      tracer.nextSpan().name("child").start().finish();
      return new ResponseEntity<>(HttpStatus.OK);
    }

    @RequestMapping(value = "/async")
    public Callable<ResponseEntity<Void>> async() throws IOException {
      return () -> new ResponseEntity<>(HttpStatus.OK);
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
  static class TracingConfig extends WebMvcConfigurerAdapter {
    @Bean HandlerInterceptor tracingInterceptor(HttpTracing httpTracing) {
      return TracingHandlerInterceptor.create(httpTracing);
    }

    @Autowired
    private HandlerInterceptor tracingInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
      registry.addInterceptor(tracingInterceptor);
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
