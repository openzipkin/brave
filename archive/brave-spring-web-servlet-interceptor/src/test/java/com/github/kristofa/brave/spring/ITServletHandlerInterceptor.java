package com.github.kristofa.brave.spring;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.LocalTracer;
import com.github.kristofa.brave.http.ITServletContainer;
import com.github.kristofa.brave.http.SpanNameProvider;
import java.io.IOException;
import java.util.concurrent.Callable;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.AssumptionViolatedException;
import org.junit.Test;
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
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

public class ITServletHandlerInterceptor extends ITServletContainer {

  @Override @Test public void addsStatusCodeWhenNotOk() throws Exception {
    throw new AssumptionViolatedException("TODO: fix error reporting");
  }

  @Controller
  static class TestController {
    final LocalTracer localTracer;

    @Autowired TestController(Brave brave) {
      this.localTracer = brave.localTracer();
    }

    @RequestMapping(value = "/foo")
    public ResponseEntity<Void> foo() throws IOException {
      return new ResponseEntity<>(HttpStatus.OK);
    }

    @RequestMapping(value = "/child")
    public ResponseEntity<Void> child() {
      localTracer.startNewSpan("child", "child");
      localTracer.finishSpan();
      return new ResponseEntity<>(HttpStatus.OK);
    }

    @RequestMapping(value = "/childAsync")
    public Callable<ResponseEntity<Void>> childAsync() throws IOException {
      return () -> {
        localTracer.startNewSpan("child", "child");
        localTracer.finishSpan();
        return new ResponseEntity<>(HttpStatus.OK);
      };
    }

    @RequestMapping(value = "/disconnect")
    public ResponseEntity<Void> disconnect() throws IOException {
      throw new IOException();
    }

    @RequestMapping(value = "/disconnectAsync")
    public Callable<ResponseEntity<Void>> disconnectAsync() throws IOException {
      return () -> {
        throw new IOException();
      };
    }
  }

  @Configuration
  @EnableWebMvc
  static class TracingConfig extends WebMvcConfigurerAdapter {
    @Bean
    ServletHandlerInterceptor tracingInterceptor(SpanNameProvider spanNameProvider, Brave brave) {
      return ServletHandlerInterceptor.builder(brave).spanNameProvider(spanNameProvider).build();
    }

    @Autowired
    private ServletHandlerInterceptor interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
      registry.addInterceptor(interceptor);
    }
  }

  @Override
  public void init(ServletContextHandler handler, Brave brave, SpanNameProvider spanNameProvider) {

    AnnotationConfigWebApplicationContext appContext =
        new AnnotationConfigWebApplicationContext() {
          // overriding this allows us to register dependencies of ServletHandlerInterceptor
          // without passing static state to a configuration class.
          @Override protected void loadBeanDefinitions(DefaultListableBeanFactory beanFactory) {
            beanFactory.registerSingleton("brave", brave);
            beanFactory.registerSingleton("spanNameProvider", spanNameProvider);
            super.loadBeanDefinitions(beanFactory);
          }
        };

    appContext.register(TestController.class); // the test resource
    appContext.register(TracingConfig.class); // generic tracing setup
    handler.addServlet(new ServletHolder(new DispatcherServlet(appContext)), "/*");
  }
}
