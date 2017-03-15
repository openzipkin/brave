package com.github.kristofa.brave.resteasy;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.LocalTracer;
import com.github.kristofa.brave.http.ITServletContainer;
import com.github.kristofa.brave.http.SpanNameProvider;
import java.io.IOException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.AssumptionViolatedException;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportResource;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

/** This class uses servlet 2.5, so needs jetty 7 dependencies. */
public class ITBraveProcessInterceptors extends ITServletContainer {
  @Override @Test public void reportsSpanOnTransportException() throws Exception {
    throw new AssumptionViolatedException("TODO: unhandled synchronous exception mapping");
  }

  @Override @Test public void addsErrorTagOnTransportException() throws Exception {
    throw new AssumptionViolatedException("TODO: error tagging");
  }

  @Override @Test public void addsErrorTagOnTransportException_async() throws Exception {
    throw new AssumptionViolatedException("TODO: error tagging");
  }

  @Override @Test public void httpUrlTagIncludesQueryParams() throws Exception {
    throw new AssumptionViolatedException("TODO: add query params to http.url");
  }

  @Override @Test public void reportsClientAddress() {
    throw new AssumptionViolatedException("PostProcessInterceptor doesn't include remote address");
  }

  @Override @Test public void reportsClientAddress_XForwardedFor() {
    throw new AssumptionViolatedException("PostProcessInterceptor doesn't include remote address");
  }

  @Override @Test public void addsStatusCodeWhenNotOk() {
    throw new AssumptionViolatedException("NotFoundException can't be caught by an interceptor");
  }

  @Override @Test public void samplingDisabled() {
    throw new AssumptionViolatedException("not reloading server context");
  }

  @Path("")
  public static class TestResource {
    final LocalTracer localTracer;

    @Autowired
    public TestResource(Brave brave) {
      this.localTracer = brave.localTracer();
    }

    @GET
    @Path("foo")
    public Response get() {
      return Response.status(200).build();
    }

    @GET
    @Path("child")
    public Response child() {
      localTracer.startNewSpan("child", "child");
      localTracer.finishSpan();
      return Response.status(200).build();
    }

    @GET
    @Path("disconnect")
    public Response disconnect() throws IOException {
      throw new IOException();
    }
  }

  @Configuration
  @EnableWebMvc
  @ImportResource({"classpath:springmvc-resteasy.xml"})
  @Import({BravePreProcessInterceptor.class, BravePostProcessInterceptor.class})
  static class TracingConfig extends WebMvcConfigurerAdapter {
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

    appContext.register(TestResource.class); // the test resource
    appContext.register(TracingConfig.class); // generic tracing setup
    handler.addServlet(new ServletHolder(new DispatcherServlet(appContext)), "/*");
  }
}
