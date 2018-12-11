package brave.test.http;

import brave.ScopedSpan;
import brave.SpanCustomizer;
import brave.Tracer;
import brave.http.HttpAdapter;
import brave.http.HttpClientParser;
import brave.http.HttpRuleSampler;
import brave.http.HttpTracing;
import brave.propagation.ExtraFieldPropagation;
import brave.sampler.Sampler;
import java.util.Arrays;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import zipkin2.Endpoint;
import zipkin2.Span;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class ITHttpClient<C> extends ITHttp {
  @Rule public MockWebServer server = new MockWebServer();

  protected C client;

  @Before public void setup() {
    httpTracing = HttpTracing.create(tracingBuilder(Sampler.ALWAYS_SAMPLE).build());
    client = newClient(server.getPort());
  }

  /** Make sure the client you return has retries disabled. */
  protected abstract C newClient(int port);

  protected abstract void closeClient(C client) throws Exception;

  protected abstract void get(C client, String pathIncludingQuery) throws Exception;

  protected abstract void post(C client, String pathIncludingQuery, String body) throws Exception;

  @Override @After public void close() throws Exception {
    closeClient(client);
    super.close();
  }

  @Test public void propagatesSpan() throws Exception {
    server.enqueue(new MockResponse());
    get(client, "/foo");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeaders().toMultimap())
        .containsKeys("x-b3-traceId", "x-b3-spanId")
        .containsEntry("x-b3-sampled", asList("1"));

    takeSpan();
  }

  @Test public void makesChildOfCurrentSpan() throws Exception {
    Tracer tracer = httpTracing.tracing().tracer();
    server.enqueue(new MockResponse());

    ScopedSpan parent = tracer.startScopedSpan("test");
    try {
      get(client, "/foo");
    } finally {
      parent.finish();
    }

    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeader("x-b3-traceId"))
        .isEqualTo(parent.context().traceIdString());
    assertThat(request.getHeader("x-b3-parentspanid"))
        .isEqualTo(parent.context().spanIdString());

    assertThat(Arrays.asList(takeSpan(), takeSpan()))
        .extracting(Span::kind)
        .containsOnly(null, Span.Kind.CLIENT);
  }

  @Test public void propagatesExtra_newTrace() throws Exception {
    Tracer tracer = httpTracing.tracing().tracer();
    server.enqueue(new MockResponse());

    ScopedSpan parent = tracer.startScopedSpan("test");
    try {
      ExtraFieldPropagation.set(parent.context(), EXTRA_KEY, "joey");
      get(client, "/foo");
    } finally {
      parent.finish();
    }

    assertThat(server.takeRequest().getHeader(EXTRA_KEY))
        .isEqualTo("joey");

    // we report one in-process and one RPC client span
    assertThat(Arrays.asList(takeSpan(), takeSpan()))
        .extracting(Span::kind)
        .containsOnly(null, Span.Kind.CLIENT);
  }

  @Test public void propagatesExtra_unsampledTrace() throws Exception {
    Tracer tracer = httpTracing.tracing().tracer().withSampler(Sampler.NEVER_SAMPLE);
    server.enqueue(new MockResponse());

    ScopedSpan parent = tracer.startScopedSpan("test");
    try {
      ExtraFieldPropagation.set(parent.context(), EXTRA_KEY, "joey");
      get(client, "/foo");
    } finally {
      parent.finish();
    }

    assertThat(server.takeRequest().getHeader(EXTRA_KEY))
        .isEqualTo("joey");
  }

  /** Unlike Brave 3, Brave 4 propagates trace ids even when unsampled */
  @Test public void propagates_sampledFalse() throws Exception {
    close();
    httpTracing = HttpTracing.create(tracingBuilder(Sampler.NEVER_SAMPLE).build());
    client = newClient(server.getPort());

    server.enqueue(new MockResponse());
    get(client, "/foo");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeaders().toMultimap())
        .containsKeys("x-b3-traceId", "x-b3-spanId")
        .doesNotContainKey("x-b3-parentSpanId")
        .containsEntry("x-b3-sampled", asList("0"));
  }

  @Test public void customSampler() throws Exception {
    close();
    httpTracing = httpTracing.toBuilder().clientSampler(HttpRuleSampler.newBuilder()
        .addRule(null, "/foo", 0.0f)
        .build()).build();
    client = newClient(server.getPort());

    server.enqueue(new MockResponse());
    get(client, "/foo");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeaders().toMultimap())
        .containsEntry("x-b3-sampled", asList("0"));
  }

  @Test public void reportsClientKindToZipkin() throws Exception {
    server.enqueue(new MockResponse());
    get(client, "/foo");

    Span span = takeSpan();
    assertThat(span.kind())
        .isEqualTo(Span.Kind.CLIENT);
  }

  @Test
  public void reportsServerAddress() throws Exception {
    server.enqueue(new MockResponse());
    get(client, "/foo");

    Span span = takeSpan();
    assertThat(span.remoteEndpoint())
        .isEqualTo(Endpoint.newBuilder()
            .ip("127.0.0.1")
            .port(server.getPort()).build()
        );
  }

  @Test public void defaultSpanNameIsMethodName() throws Exception {
    server.enqueue(new MockResponse());
    get(client, "/foo");

    Span span = takeSpan();
    assertThat(span.name())
        .isEqualTo("get");
  }

  @Test public void supportsPortableCustomization() throws Exception {
    String uri = "/foo?z=2&yAA=1";

    close();
    httpTracing = httpTracing.toBuilder()
        .clientParser(new HttpClientParser() {
          @Override
          public <Req> void request(HttpAdapter<Req, ?> adapter, Req req,
              SpanCustomizer customizer) {
            customizer.name(adapter.method(req).toLowerCase() + " " + adapter.path(req));
            customizer.tag("http.url", adapter.url(req)); // just the path is logged by default
            customizer.tag("context.visible", String.valueOf(currentTraceContext.get() != null));
            customizer.tag("request_customizer.is_span", (customizer instanceof brave.Span) + "");
          }

          @Override
          public <Resp> void response(HttpAdapter<?, Resp> adapter, Resp res, Throwable error,
              SpanCustomizer customizer) {
            super.response(adapter, res, error, customizer);
            customizer.tag("response_customizer.is_span", (customizer instanceof brave.Span) + "");
          }
        })
        .build().clientOf("remote-service");

    client = newClient(server.getPort());
    server.enqueue(new MockResponse());
    get(client, uri);

    Span span = takeSpan();
    assertThat(span.name())
        .isEqualTo("get /foo");

    assertThat(span.remoteServiceName())
        .isEqualTo("remote-service");

    assertThat(span.tags())
        .containsEntry("http.url", url(uri))
        .containsEntry("context.visible", "true")
        .containsEntry("request_customizer.is_span", "false")
        .containsEntry("response_customizer.is_span", "false");
  }

  @Test public void addsStatusCodeWhenNotOk() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(400));

    try {
      get(client, "/foo");
    } catch (Exception e) {
      // some clients think 400 is an error
    }

    Span span = takeSpan();
    assertThat(span.tags())
        .containsEntry("http.status_code", "400")
        .containsEntry("error", "400");
  }

  @Test public void redirect() throws Exception {
    Tracer tracer = httpTracing.tracing().tracer();
    server.enqueue(new MockResponse().setResponseCode(302)
        .addHeader("Location: " + url("/bar")));
    server.enqueue(new MockResponse().setResponseCode(404)); // hehe to a bad location!

    ScopedSpan parent = tracer.startScopedSpan("test");
    try {
      get(client, "/foo");
    } catch (RuntimeException e) {
      // some think 404 is an exception
    } finally {
      parent.finish();
    }

    Span client1 = takeSpan();
    Span client2 = takeSpan();
    assertThat(Arrays.asList(client1.tags().get("http.path"), client2.tags().get("http.path")))
        .contains("/foo", "/bar");

    assertThat(takeSpan().kind()).isNull(); // local
  }

  @Test public void post() throws Exception {
    String path = "/post";
    String body = "body";
    server.enqueue(new MockResponse());

    post(client, path, body);

    assertThat(server.takeRequest().getBody().readUtf8())
        .isEqualTo(body);

    Span span = takeSpan();
    assertThat(span.name())
        .isEqualTo("post");
  }

  @Test public void reportsSpanOnTransportException() throws Exception {
    checkReportsSpanOnTransportException();
  }

  protected Span checkReportsSpanOnTransportException() throws InterruptedException {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

    try {
      get(client, "/foo");
    } catch (Exception e) {
      // ok, but the span should include an error!
    }

    return takeSpan();
  }

  @Test public void addsErrorTagOnTransportException() throws Exception {
    Span span = checkReportsSpanOnTransportException();
    assertThat(span.tags())
        .containsKey("error");
  }

  @Test public void httpPathTagExcludesQueryParams() throws Exception {
    String path = "/foo?z=2&yAA=1";

    server.enqueue(new MockResponse());
    get(client, path);

    Span span = takeSpan();
    assertThat(span.tags())
        .containsEntry("http.path", "/foo");
  }

  protected String url(String pathIncludingQuery) {
    return "http://127.0.0.1:" + server.getPort() + pathIncludingQuery;
  }
}
