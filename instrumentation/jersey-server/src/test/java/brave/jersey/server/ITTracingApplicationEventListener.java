package brave.jersey.server;

import brave.Tracer;
import brave.http.HttpTracing;
import brave.http.ITServletContainer;
import brave.propagation.ExtraFieldPropagation;
import java.io.IOException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Response;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ManagedAsync;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.junit.AssumptionViolatedException;
import org.junit.Test;

public class ITTracingApplicationEventListener extends ITServletContainer {

  @Override @Test public void reportsClientAddress() {
    throw new AssumptionViolatedException("TODO!");
  }

  /** Tests that the span propagates between under asynchronous callbacks managed by jersey. */
  @Test public void managedAsync() throws Exception {
    get("/managedAsync");

    takeSpan();
  }

  @Override public void init(ServletContextHandler handler) {
    ResourceConfig config = new ResourceConfig();
    config.register(new TestResource(httpTracing));
    config.register(TracingApplicationEventListener.create(httpTracing));
    handler.addServlet(new ServletHolder(new ServletContainer(config)), "/*");
  }

  @Path("")
  public static class TestResource {
    final Tracer tracer;

    TestResource(HttpTracing httpTracing) {
      this.tracer = httpTracing.tracing().tracer();
    }

    @GET
    @Path("foo")
    public Response foo() {
      return Response.ok().build();
    }

    @GET
    @Path("extra")
    public Response extra() {
      return Response.ok(ExtraFieldPropagation.get(EXTRA_KEY)).build();
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
      System.err.println("ASDFASDFASDFASDFASD " + Thread.currentThread());
      new Thread(() -> response.resume("foo")).start();
    }

    @GET
    @Path("managedAsync")
    @ManagedAsync
    public void managedAsync(@Suspended AsyncResponse response) throws IOException {
      response.resume("foo");
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
}
