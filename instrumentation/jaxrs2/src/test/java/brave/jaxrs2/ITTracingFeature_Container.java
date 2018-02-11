package brave.jaxrs2;

import brave.Tracer;
import brave.http.HttpAdapter;
import brave.http.HttpServerParser;
import brave.http.HttpTracing;
import brave.http.ITServletContainer;
import brave.propagation.ExtraFieldPropagation;
import java.io.IOException;
import java.lang.reflect.Method;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Response;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.junit.AssumptionViolatedException;
import org.junit.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

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
      new Thread(() -> response.resume(Response.status(200).build())).start();
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

  @Test
  public void declarative_namingPolicy() throws Exception {
    httpTracing = httpTracing.toBuilder().serverParser(new HttpServerParser() {
      @Override
      public <Req> String spanName(HttpAdapter<Req, ?> adapter, Req req) {
        Method method = ((ContainerAdapter) adapter).resourceMethod(req);
        return method.getName().toLowerCase();
      }
    }).build();
    init();

    get("/foo");

    Span span = takeSpan();
    assertThat(span.name())
        .isEqualTo("foo");
  }

  @Override public void init(ServletContextHandler handler) {
    // Adds application programmatically as opposed to using web.xml
    handler.addServlet(new ServletHolder(new HttpServletDispatcher()), "/*");
    handler.addEventListener(new TracingBootstrap(httpTracing, new TestResource(httpTracing)));
  }
}
