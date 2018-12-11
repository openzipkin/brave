package brave.http;

import brave.Tracing;
import io.undertow.Undertow;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(2)
@State(Scope.Benchmark)
public abstract class HttpServerBenchmarks {

  Undertow server;
  OkHttpClient client;
  String baseUrl;

  protected String baseUrl() {
    return baseUrl;
  }

  @Setup(Level.Trial) public void init() throws Exception {
    baseUrl = "http://127.0.0.1:" + initServer();
    client = new OkHttpClient();
  }

  @TearDown(Level.Trial) public void close() throws Exception {
    if (server != null) server.stop();
    client.dispatcher().executorService().shutdown();
    if (Tracing.current() != null) Tracing.current().close();
  }

  protected int initServer() throws Exception {
    DeploymentInfo servletBuilder = Servlets.deployment()
        .setClassLoader(getClass().getClassLoader())
        .setContextPath("/")
        .setDeploymentName("test.war");

    init(servletBuilder);

    DeploymentManager manager = Servlets.defaultContainer().addDeployment(servletBuilder);
    manager.deploy();
    server = Undertow.builder()
        .addHttpListener(0, "127.0.0.1")
        .setHandler(manager.start()).build();
    server.start();
    return ((InetSocketAddress) server.getListenerInfo().get(0).getAddress()).getPort();
  }

  protected abstract void init(DeploymentInfo servletBuilder);

  @Benchmark public void server_get() throws Exception {
    get("/nottraced");
  }

  @Benchmark public void unsampledServer_get() throws Exception {
    get("/unsampled");
  }

  @Benchmark public void onlySampledLocalServer_get() throws Exception {
    get("/onlysampledlocal");
  }

  @Benchmark public void tracedServer_get() throws Exception {
    get("/traced");
  }

  @Benchmark public void tracedExtraServer_get() throws Exception {
    get("/tracedextra");
  }

  @Benchmark public void tracedCorrelatedServer_get() throws Exception {
    get("/tracedcorrelated");
  }

  @Benchmark public void tracedExtraServer_get_request_id() throws Exception {
    client.newCall(new Request.Builder().url(baseUrl() + "/tracedextra")
        .header("x-vcap-request-id", "216a2aea45d08fc9")
        .build())
        .execute().body().close();
  }

  @Benchmark public void tracedServer_get_resumeTrace() throws Exception {
    client.newCall(new Request.Builder().url(baseUrl() + "/traced")
        .header("X-B3-TraceId", "216a2aea45d08fc9")
        .header("X-B3-SpanId", "5b4185666d50f68b")
        .header("X-B3-Sampled", "1")
        .build())
        .execute().body().close();
  }

  @Benchmark public void traced128Server_get() throws Exception {
    get("/traced128");
  }

  @Benchmark public void traced128Server_get_resumeTrace() throws Exception {
    client.newCall(new Request.Builder().url(baseUrl() + "/traced128")
        .header("X-B3-TraceId", "5759e988b62e8f6a216a2aea45d08fc9")
        .header("X-B3-SpanId", "5b4185666d50f68b")
        .header("X-B3-Sampled", "1")
        .build())
        .execute().body().close();
  }

  void get(String path) throws IOException {
    client.newCall(new Request.Builder().url(baseUrl() + path).build()).execute().body().close();
  }
}
