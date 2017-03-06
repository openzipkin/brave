package com.github.kristofa.brave.resteasy3;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.LocalTracer;
import com.github.kristofa.brave.http.ITServletContainer;
import com.github.kristofa.brave.http.SpanNameProvider;
import java.io.IOException;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.jboss.resteasy.plugins.server.servlet.ResteasyBootstrap;
import org.jboss.resteasy.plugins.spring.SpringContextLoaderListener;
import org.junit.AssumptionViolatedException;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

public class ITBraveTracingFeature_Server extends ITServletContainer {

  @Override @Test public void reportsClientAddress() {
    throw new AssumptionViolatedException("ContainerRequestContext doesn't include remote address");
  }

  /**
   * {@link ContainerResponseFilter} has no means to handle uncaught exceptions. Unless you provide
   * a catch-all exception mapper, requests that result in unhandled exceptions will leak until they
   * are eventually flushed.
   */
  @Provider
  public static class CatchAllExceptions implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception e) {
      if (e instanceof WebApplicationException) {
        return ((WebApplicationException) e).getResponse();
      }

      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("Internal error")
          .type("text/plain")
          .build();
    }
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

  @Override
  public void init(ServletContextHandler handler, Brave brave, SpanNameProvider spanNameProvider) {

    AnnotationConfigWebApplicationContext appContext =
        new AnnotationConfigWebApplicationContext() {
          // overriding this allows us to register dependencies of BraveTracingFeatureConfiguration
          // without passing static state to a configuration class.
          @Override protected void loadBeanDefinitions(DefaultListableBeanFactory beanFactory) {
            beanFactory.registerSingleton("brave", brave);
            beanFactory.registerSingleton("spanNameProvider", spanNameProvider);
            super.loadBeanDefinitions(beanFactory);
          }
        };

    appContext.register(TestResource.class); // the test resource
    appContext.register(CatchAllExceptions.class);
    appContext.register(BraveTracingFeatureConfiguration.class); // generic tracing setup

    // resteasy + spring configuration, programmatically as opposed to using web.xml
    handler.addServlet(new ServletHolder(new HttpServletDispatcher()), "/*");
    handler.addEventListener(new ResteasyBootstrap());
    handler.addEventListener(new SpringContextLoaderListener(appContext));
  }
}
