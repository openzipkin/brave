package brave.jaxrs2.features.sampling;

import brave.Tracing;
import brave.http.HttpAdapter;
import brave.http.HttpSampler;
import brave.http.HttpTracing;
import brave.http.ServletContainer;
import brave.jaxrs2.TracingBootstrap;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedDeque;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public class ITDeclarativeSampling extends ServletContainer {
  ConcurrentLinkedDeque<Span> spans = new ConcurrentLinkedDeque<>();
  OkHttpClient client = new OkHttpClient();
  HttpTracing httpTracing = HttpTracing.newBuilder(Tracing.newBuilder()
      .spanReporter(spans::add)
      .build())
      .serverSampler(new Traced.Sampler(new HttpSampler() {
        @Override public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request) {
          return !"/foo".equals(adapter.path(request));
        }
      })).build();

  @After public void close(){
    Tracing.current().close();
  }

  @Path("")
  public static class Resource { // public for resteasy to inject

    @GET
    @Path("foo")
    @Traced // overrides path-based decision to not trace /foo
    public String foo() {
      return "";
    }

    @GET
    @Path("bar")
    @Traced(enabled = false) // overrides path-based decision to trace /bar
    public String bar() {
      return "";
    }

    @GET
    @Path("baz")
    public String baz() {
      return "";
    }
  }

  @Test
  public void annotationOverridesHttpSampler() throws Exception {
    get("/bar");
    assertThat(spans).isEmpty();

    get("/foo");
    assertThat(spans).isNotEmpty();
  }

  @Test
  public void lackOfAnnotationFallsback() throws Exception {
    get("/baz");
    assertThat(spans).isNotEmpty();
  }

  @Override public void init(ServletContextHandler handler) {
    // Adds application programmatically as opposed to using web.xml
    handler.addServlet(new ServletHolder(new HttpServletDispatcher()), "/*");
    handler.addEventListener(new TracingBootstrap(httpTracing, new Resource()));
  }

  @Before public void setUp() {
    super.init();
  }

  @After public void tearDown() {
    super.stop();
  }

  Response get(String path) throws IOException {
    try (Response response = client.newCall(new Request.Builder().url(url(path)).build())
        .execute()) {

      return response;
    }
  }
}
