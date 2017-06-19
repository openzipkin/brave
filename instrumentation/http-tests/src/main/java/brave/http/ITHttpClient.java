package brave.http;

import brave.SpanCustomizer;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.internal.HexCodec;
import brave.sampler.Sampler;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import zipkin.Constants;
import zipkin.Endpoint;
import zipkin.TraceKeys;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class ITHttpClient<C> extends ITHttp {
  protected C client;

  @Before public void setup() {
    httpTracing = HttpTracing.create(tracingBuilder(Sampler.ALWAYS_SAMPLE).build());
    client = newClient(server.getPort());
  }

  /** Make sure the client you return has retries disabled. */
  protected abstract C newClient(int port);

  protected abstract void closeClient(C client) throws IOException;

  protected abstract void get(C client, String pathIncludingQuery) throws Exception;

  protected abstract void post(C client, String pathIncludingQuery, String body) throws Exception;

  protected abstract void getAsync(C client, String pathIncludingQuery) throws Exception;

  @After public void close() throws IOException {
    closeClient(client);
  }

  @Test public void propagatesSpan() throws Exception {
    server.enqueue(new MockResponse());
    get(client, "/foo");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeaders().toMultimap())
        .containsKeys("x-b3-traceId", "x-b3-spanId")
        .containsEntry("x-b3-sampled", asList("1"));
  }

  @Test public void makesChildOfCurrentSpan() throws Exception {
    Tracer tracer = httpTracing.tracing().tracer();
    server.enqueue(new MockResponse());

    brave.Span parent = tracer.newTrace().name("test").start();
    try (SpanInScope ws = tracer.withSpanInScope(parent)) {
      get(client, "/foo");
    } finally {
      parent.finish();
    }

    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeader("x-b3-traceId"))
        .isEqualTo(parent.context().traceIdString());
    assertThat(request.getHeader("x-b3-parentspanid"))
        .isEqualTo(HexCodec.toLowerHex(parent.context().spanId()));
  }

  /**
   * This tests that the parent is determined at the time the request was made, not when the request
   * was executed.
   */
  @Test public void usesParentFromInvocationTime() throws Exception {
    Tracer tracer = httpTracing.tracing().tracer();
    server.enqueue(new MockResponse().setBodyDelay(1, TimeUnit.SECONDS));
    server.enqueue(new MockResponse());

    brave.Span parent = tracer.newTrace().name("test").start();
    try (SpanInScope ws = tracer.withSpanInScope(parent)) {
      getAsync(client, "/foo");
      getAsync(client, "/foo");
    } finally {
      parent.finish();
    }

    brave.Span otherSpan = tracer.newTrace().name("test2").start();
    try (SpanInScope ws = tracer.withSpanInScope(otherSpan)) {
      for (int i = 0; i < 2; i++) {
        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("x-b3-traceId"))
            .isEqualTo(parent.context().traceIdString());
        assertThat(request.getHeader("x-b3-parentspanid"))
            .isEqualTo(HexCodec.toLowerHex(parent.context().spanId()));
      }
    } finally {
      otherSpan.finish();
    }
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

  @Test public void reportsClientAnnotationsToZipkin() throws Exception {
    server.enqueue(new MockResponse());
    get(client, "/foo");

    assertThat(spans)
        .flatExtracting(s -> s.annotations)
        .extracting(a -> a.value)
        .containsExactly("cs", "cr");
  }

  @Test
  public void reportsServerAddress() throws Exception {
    server.enqueue(new MockResponse());
    get(client, "/foo");

    assertThat(spans)
        .flatExtracting(s -> s.binaryAnnotations)
        .filteredOn(b -> b.key.equals(Constants.SERVER_ADDR))
        .extracting(b -> b.endpoint)
        .containsExactly(Endpoint.builder()
            .serviceName("")
            .ipv4(127 << 24 | 1)
            .port(server.getPort()).build()
        );
  }

  @Test public void defaultSpanNameIsMethodName() throws Exception {
    server.enqueue(new MockResponse());
    get(client, "/foo");

    assertThat(spans)
        .extracting(s -> s.name)
        .containsExactly("get");
  }

  @Test public void supportsPortableCustomization() throws Exception {
    String uri = "/foo?z=2&yAA=1";

    close();
    httpTracing = httpTracing.toBuilder()
        .clientParser(new HttpClientParser() {
          @Override
          public <Req> void request(HttpAdapter<Req, ?> adapter, Req req, SpanCustomizer customizer) {
            customizer.name(adapter.method(req).toLowerCase() + " " + adapter.path(req));
            customizer.tag(TraceKeys.HTTP_URL, adapter.url(req)); // just the path is logged by default
          }
        })
        .build().clientOf("remote-service");

    client = newClient(server.getPort());
    server.enqueue(new MockResponse());
    get(client, uri);

    assertThat(spans)
        .extracting(s -> s.name)
        .containsExactly("get /foo");

    assertThat(spans)
        .flatExtracting(s -> s.binaryAnnotations)
        .filteredOn(b -> b.key.equals(Constants.SERVER_ADDR))
        .extracting(b -> b.endpoint.serviceName)
        .containsExactly("remote-service");

    assertReportedTagsInclude(TraceKeys.HTTP_URL, url(uri));
  }

  @Test public void addsStatusCodeWhenNotOk() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(400));

    try {
      get(client, "/foo");
    } catch (RuntimeException e) {
      // some clients think 400 is an error
    }

    assertReportedTagsInclude(TraceKeys.HTTP_STATUS_CODE, "400");
    assertReportedTagsInclude(Constants.ERROR, "400");
  }

  @Test public void redirect() throws Exception {
    Tracer tracer = httpTracing.tracing().tracer();
    server.enqueue(new MockResponse().setResponseCode(302)
        .addHeader("Location: " + url("/bar")));
    server.enqueue(new MockResponse().setResponseCode(404)); // hehe to a bad location!

    brave.Span parent = tracer.newTrace().name("test").start();
    try (Tracer.SpanInScope ws = tracer.withSpanInScope(parent)) {
      get(client, "/foo");
    } catch (RuntimeException e) {
      // some think 404 is an exception
    } finally {
      parent.finish();
    }

    assertReportedTagsInclude(TraceKeys.HTTP_PATH, "/foo", "/bar");
  }

  @Test public void post() throws Exception {
    String path = "/post";
    String body = "body";
    server.enqueue(new MockResponse());

    post(client, path, body);

    assertThat(server.takeRequest().getBody().readUtf8())
        .isEqualTo(body);

    assertThat(spans)
        .extracting(s -> s.name)
        .containsExactly("post");
  }

  @Test public void reportsSpanOnTransportException() throws Exception {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

    try {
      get(client, "/foo");
    } catch (Exception e) {
      // ok, but the span should include an error!
    }

    assertThat(spans).hasSize(1);
  }

  @Test public void addsErrorTagOnTransportException() throws Exception {
    reportsSpanOnTransportException();

    assertThat(spans)
        .flatExtracting(s -> s.binaryAnnotations)
        .extracting(b -> b.key)
        .contains(Constants.ERROR);
  }

  @Test public void httpPathTagExcludesQueryParams() throws Exception {
    String path = "/foo?z=2&yAA=1";

    server.enqueue(new MockResponse());
    get(client, path);

    assertReportedTagsInclude(TraceKeys.HTTP_PATH, "/foo");
  }

  protected String url(String pathIncludingQuery) {
    return "http://127.0.0.1:" + server.getPort() + pathIncludingQuery;
  }
}
