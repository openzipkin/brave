package brave.features.http;

import brave.Tracing;
import brave.http.HttpAdapter;
import brave.http.HttpSampler;
import brave.http.HttpTracing;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedDeque;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import zipkin.Endpoint;
import zipkin.internal.Util;

import static org.assertj.core.api.Assertions.assertThat;

/** This is an example of http request sampling */
public class RequestSamplingTest {
  @Rule public MockWebServer server = new MockWebServer();

  ConcurrentLinkedDeque<zipkin.Span> spans = new ConcurrentLinkedDeque<>();
  Tracing tracing = Tracing.newBuilder()
      .localEndpoint(Endpoint.builder().serviceName("server").build())
      .reporter(spans::push)
      .build();
  HttpTracing httpTracing = HttpTracing.newBuilder(tracing)
      // server starts traces under the path /api
      .serverSampler(new HttpSampler() {
        @Override public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request) {
          return adapter.path(request).startsWith("/api");
        }
      })
      // client doesn't start new traces
      .clientSampler(HttpSampler.NEVER_SAMPLE)
      .build();

  OkHttpClient client = new OkHttpClient();

  @Before public void setup() {
    server.setDispatcher(new TracingDispatcher(httpTracing, new Dispatcher() {
      OkHttpClient tracedClient = client.newBuilder()
          .addNetworkInterceptor(new TracingInterceptor(httpTracing)).build();

      @Override public MockResponse dispatch(RecordedRequest request) {
        if (request.getPath().equals("/next")) return new MockResponse().setBody("next");
        Call next = tracedClient.newCall(new Request.Builder().url(server.url("/next")).build());
        try (ResponseBody responseBody = next.execute().body()) {
          return new MockResponse().setBody(responseBody.string());
        } catch (IOException e) {
          return new MockResponse().setBody(e.getMessage()).setResponseCode(500);
        }
      }
    }));
  }

  @Test public void serverDoesntTraceFoo() throws Exception {
    callServer("/foo");
    assertThat(spans).isEmpty();
  }

  @Test public void clientTracedWhenServerIs() throws Exception {
    callServer("/api");

    assertThat(spans)
        .flatExtracting(s -> s.binaryAnnotations)
        .filteredOn(b -> b.key.equals("http.path"))
        .extracting(b -> new String(b.value, Util.UTF_8))
        .containsOnly("/api", "/next");
  }

  void callServer(String path) throws IOException {
    Call next = client.newCall(new Request.Builder().url(server.url(path)).build());
    try (ResponseBody responseBody = next.execute().body()) {
      assertThat(responseBody.string()).isEqualTo("next");
    }
  }
}
