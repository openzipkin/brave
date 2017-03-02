package com.github.kristofa.brave.jersey2;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.LocalTracer;
import com.github.kristofa.brave.http.ITServletContainer;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.github.kristofa.brave.jaxrs2.BraveTracingFeature;
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
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.junit.AssumptionViolatedException;
import org.junit.Test;

public class ITBraveTracingFeature_Server extends ITServletContainer {

  @Override @Test public void reportsClientAddress() {
    throw new AssumptionViolatedException("TODO: fix client address");
  }

  @Path("")
  public static class TestResource {
    final LocalTracer localTracer;

    public TestResource(@Context ServletContext context) {
      this.localTracer = ((Brave) context.getAttribute("brave")).localTracer();
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

  @Override
  public void init(ServletContextHandler handler, Brave brave, SpanNameProvider spanNameProvider) {

    ResourceConfig config = new ResourceConfig()
        .register(TestResource.class)
        .register(CatchAllExceptions.class)
        .register(BraveTracingFeature.builder(brave)
            .spanNameProvider(spanNameProvider)
            .build()
        );

    handler.addServlet(new ServletHolder(new ServletContainer(config)), "/*");
    handler.setAttribute("brave", brave); // for TestResource
  }
}
