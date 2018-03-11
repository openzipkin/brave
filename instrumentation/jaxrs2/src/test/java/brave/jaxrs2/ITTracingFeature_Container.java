package brave.jaxrs2;

import brave.http.HttpAdapter;
import brave.http.HttpServerParser;
import brave.test.http.ITServletContainer;
import java.lang.reflect.Method;
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

  @Test public void tagsResource() throws Exception {
    get("/foo");

    Span span = takeSpan();
    assertThat(span.tags())
        .containsEntry("jaxrs.resource.class", "TestResource")
        .containsEntry("jaxrs.resource.method", "foo");
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
