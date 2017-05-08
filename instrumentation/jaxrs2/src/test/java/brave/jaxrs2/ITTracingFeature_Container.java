package brave.jaxrs2;

import brave.Tracer;
import brave.http.HttpTracing;
import brave.http.ITServletContainer;
import java.io.IOException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.Suspended;
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
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

public class ITTracingFeature_Container extends ITServletContainer {

  @Override @Test public void reportsClientAddress() {
    throw new AssumptionViolatedException("ContainerRequestContext doesn't include remote address");
  }

  @Path("")
  public static class TestResource { // public for resteasy to inject
    final Tracer tracer;

    TestResource(HttpTracing httpTracing) {
      this.tracer = httpTracing.tracing().tracer();
    }

    @GET
    @Path("foo")
    public Response get() {
      return Response.status(200).build();
    }

    @GET
    @Path("badrequest")
    public Response badrequest() {
      return Response.status(400).build();
    }

    @GET
    @Path("child")
    public Response child() {
      tracer.nextSpan().name("child").start().finish();
      return Response.status(200).build();
    }

    @GET
    @Path("async")
    public void async(@Suspended AsyncResponse response) throws IOException {
      new Thread(() -> response.resume("ok")).start();
    }

    @GET
    @Path("exception")
    public Response disconnect() throws IOException {
      throw new IOException();
    }

    @GET
    @Path("exceptionAsync")
    public void disconnectAsync(@Suspended AsyncResponse response) throws IOException {
      new Thread(() -> response.resume(new IOException())).start();
    }
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

  /** Imports jaxrs2 filters used in resteasy3. */
  @Configuration
  @Import(TracingFeature.class)
  public static class TracingFeatureConfiguration {
  }

  @Override public void init(ServletContextHandler handler) {
    AnnotationConfigWebApplicationContext appContext =
        new AnnotationConfigWebApplicationContext() {
          // overriding this allows us to register dependencies of TracingFeature
          // without passing static state to a configuration class.
          @Override protected void loadBeanDefinitions(DefaultListableBeanFactory beanFactory) {
            beanFactory.registerSingleton("httpTracing", httpTracing);
            super.loadBeanDefinitions(beanFactory);
          }
        };

    appContext.register(TestResource.class); // the test resource
    appContext.register(CatchAllExceptions.class);
    appContext.register(TracingFeatureConfiguration.class); // generic tracing setup

    // resteasy + spring configuration, programmatically as opposed to using web.xml
    handler.addServlet(new ServletHolder(new HttpServletDispatcher()), "/*");
    handler.addEventListener(new ResteasyBootstrap());
    handler.addEventListener(new SpringContextLoaderListener(appContext));
  }
}
