package brave.jaxrs2;

import brave.Tracing;
import brave.http.HttpServerBenchmarks;
import brave.sampler.Sampler;
import io.undertow.Undertow;
import io.undertow.servlet.api.DeploymentInfo;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.servlet.ServletException;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zipkin.reporter.Reporter;

public class JaxRs2ServerBenchmarks extends HttpServerBenchmarks {

  @Path("")
  public static class Resource {
    @GET @Produces("text/plain; charset=UTF-8") public String get() {
      return "hello world";
    }
  }

  @ApplicationPath("/nottraced")
  public static class App extends Application {
    @Override public Set<Object> getSingletons() {
      return Collections.singleton(new Resource());
    }
  }

  @ApplicationPath("/unsampled")
  public static class Unsampled extends Application {
    @Override public Set<Object> getSingletons() {
      return new LinkedHashSet<>(Arrays.asList(new Resource(), TracingFeature.create(
          Tracing.newBuilder().sampler(Sampler.NEVER_SAMPLE).reporter(Reporter.NOOP).build()
      )));
    }
  }

  @ApplicationPath("/traced")
  public static class TracedApp extends Application {
    @Override public Set<Object> getSingletons() {
      return new LinkedHashSet<>(Arrays.asList(new Resource(), TracingFeature.create(
          Tracing.newBuilder().reporter(Reporter.NOOP).build()
      )));
    }
  }

  PortExposing server;

  @Override protected int initServer() throws ServletException {
    server = (PortExposing) new PortExposing()
        .deploy(App.class)
        .deploy(Unsampled.class)
        .deploy(TracedApp.class)
        .start(Undertow.builder().addHttpListener(8888, "127.0.0.1"));
    return server.getPort();
  }

  static class PortExposing extends UndertowJaxrsServer {
    int getPort() {
      return ((InetSocketAddress) server.getListenerInfo().get(0).getAddress()).getPort();
    }
  }

  @Override protected void init(DeploymentInfo servletBuilder) {
  }

  @TearDown(Level.Trial) @Override public void close() throws Exception {
    server.stop();
    super.close();
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .include(".*" + JaxRs2ServerBenchmarks.class.getSimpleName() + ".*")
        .build();

    new Runner(opt).run();
  }
}
