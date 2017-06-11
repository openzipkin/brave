package brave.jaxrs2;

import brave.Tracer;
import brave.http.HttpTracing;
import brave.http.ITServletContainer;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.jboss.resteasy.plugins.server.servlet.ListenerBootstrap;
import org.jboss.resteasy.plugins.server.servlet.ResteasyBootstrap;
import org.jboss.resteasy.spi.ResteasyConfiguration;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.junit.AssumptionViolatedException;
import org.junit.Test;

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

  @Override public void init(ServletContextHandler handler) {


    // Adds application programmatically as opposed to using web.xml
    handler.addServlet(new ServletHolder(new HttpServletDispatcher()), "/*");
    handler.addEventListener(new ResteasyBootstrap() {
      @Override public void contextInitialized(ServletContextEvent event) {
        deployment = new ResteasyDeployment();
        deployment.setApplication(new Application(){
          @Override public Set<Object> getSingletons() {
            return new LinkedHashSet<>(Arrays.asList(
                new TestResource(httpTracing),
                new CatchAllExceptions(),
                new TracingFeature(httpTracing)
            ));
          }
        });
        ServletContext servletContext = event.getServletContext();
        ListenerBootstrap config = new ListenerBootstrap(servletContext);
        servletContext.setAttribute(ResteasyDeployment.class.getName(), deployment);
        deployment.getDefaultContextObjects().put(ResteasyConfiguration.class, config);
        config.createDeployment();
        deployment.start();
      }
    });
  }
}
