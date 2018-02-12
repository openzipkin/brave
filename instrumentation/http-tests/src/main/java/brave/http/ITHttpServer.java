package brave.http;

import brave.SpanCustomizer;
import brave.propagation.ExtraFieldPropagation;
import brave.sampler.Sampler;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.http.HttpHeaders;
import okio.Buffer;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Endpoint;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class ITHttpServer extends ITHttp {
  OkHttpClient client = new OkHttpClient();

  @Before public void setup() throws Exception {
    httpTracing = HttpTracing.create(tracingBuilder(Sampler.ALWAYS_SAMPLE).build());
    init();
  }

  /** recreate the server if needed */
  protected abstract void init() throws Exception;

  protected abstract String url(String path);

  @Test
  public void usesExistingTraceId() throws Exception {
    String path = "/foo";

    final String traceId = "463ac35c9f6413ad";
    final String parentId = traceId;
    final String spanId = "48485a3953bb6124";

    get(new Request.Builder().url(url(path))
        .header("X-B3-TraceId", traceId)
        .header("X-B3-ParentSpanId", parentId)
        .header("X-B3-SpanId", spanId)
        .header("X-B3-Sampled", "1")
        .build());

    Span span = takeSpan();
    assertThat(span.traceId()).isEqualTo(traceId);
    assertThat(span.parentId()).isEqualTo(parentId);
    assertThat(span.id()).isEqualTo(spanId);
  }

  @Test
  public void readsExtra_newTrace() throws Exception {
    readsExtra(new Request.Builder());

    takeSpan();
  }

  @Test
  public void readsExtra_unsampled() throws Exception {
    readsExtra(new Request.Builder()
        .header("X-B3-Sampled", "0"));

    // @After will check that nothing is reported
  }

  @Test
  public void readsExtra_existingTrace() throws Exception {
    String traceId = "463ac35c9f6413ad";

    readsExtra(new Request.Builder()
        .header("X-B3-TraceId", traceId)
        .header("X-B3-SpanId", traceId));

    Span span = takeSpan();
    assertThat(span.traceId()).isEqualTo(traceId);
    assertThat(span.id()).isEqualTo(traceId);
  }

  /**
   * The /extra endpoint should copy the key {@link #EXTRA_KEY} to the response body using
   * {@link ExtraFieldPropagation#get(String)}.
   */
  void readsExtra(Request.Builder builder) throws Exception {
    Request request = builder.url(url("/extra"))
        // this is the pre-configured key we can pass through
        .header(EXTRA_KEY, "joey").build();

    Response response = get(request);
    assertThat(response.isSuccessful()).isTrue();
    // if we can read the response header, the server must have been able to copy it
    assertThat(response.body().source().readUtf8())
        .isEqualTo("joey");
  }

  @Test
  public void samplingDisabled() throws Exception {
    httpTracing = HttpTracing.create(tracingBuilder(Sampler.NEVER_SAMPLE).build());
    init();

    get("/foo");

    // @After will check that nothing is reported
  }

  @Test public void customSampler() throws Exception {
    String path = "/foo";

    httpTracing = httpTracing.toBuilder().serverSampler(HttpRuleSampler.newBuilder()
        .addRule(null, path, 0.0f)
        .build()).build();
    init();

    Request request = new Request.Builder().url(url(path)).build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }

    // @After will check that nothing is reported
  }

  /**
   * Tests that the span propagates between under asynchronous callbacks (even if explicitly)
   */
  @Test
  public void async() throws Exception {
    get("/async");

    takeSpan();
  }

  /**
   * This ensures thread-state is propagated from trace interceptors to user code. The endpoint
   * "/child" is expected to create a local span. When this works, it should be a child of the
   * "current span", in this case the span representing an incoming server request. When thread
   * state isn't managed properly, the child span will appear as a new trace.
   */
  @Test
  public void createsChildSpan() throws Exception {
    get("/child");

    Span child = takeSpan();
    Span parent = takeSpan();

    assertThat(parent.traceId()).isEqualTo(child.traceId());
    assertThat(parent.id()).isEqualTo(child.parentId());
    assertThat(parent.timestamp()).isLessThan(child.timestamp());
    assertThat(parent.duration()).isGreaterThan(child.duration());
  }

  @Test
  public void reportsClientAddress() throws Exception {
    get("/foo");

    Span span = takeSpan();
    assertThat(span.remoteEndpoint())
        .isNotNull();
  }

  @Test
  public void reportsClientAddress_XForwardedFor() throws Exception {
    get(new Request.Builder().url(url("/foo"))
        .header("X-Forwarded-For", "1.2.3.4")
        .build());

    Span span = takeSpan();
    assertThat(span.remoteEndpoint())
        .extracting(Endpoint::ipv4)
        .contains("1.2.3.4");
  }

  @Test
  public void reportsServerKindToZipkin() throws Exception {
    get("/foo");

    Span span = takeSpan();
    assertThat(span.kind())
        .isEqualTo(Span.Kind.SERVER);
  }

  @Test
  public void defaultSpanNameIsMethodName() throws Exception {
    get("/foo");

    Span span = takeSpan();
    assertThat(span.name())
        .isEqualTo("get");
  }

  @Test
  public void supportsPortableCustomization() throws Exception {
    httpTracing = httpTracing.toBuilder().serverParser(new HttpServerParser() {
      @Override
      public <Req> void request(HttpAdapter<Req, ?> adapter, Req req, SpanCustomizer customizer) {
        customizer.name(adapter.method(req).toLowerCase() + " " + adapter.path(req));
        customizer.tag("http.url", adapter.url(req)); // just the path is logged by default
        customizer.tag("context.visible", String.valueOf(currentTraceContext.get() != null));
      }
    }).build();
    init();

    String uri = "/foo?z=2&yAA=1";
    get(uri);

    Span span = takeSpan();
    assertThat(span.name())
        .isEqualTo("get /foo");
    assertThat(span.tags())
        .containsEntry("http.url", url(uri))
        .containsEntry("context.visible", "true");
  }

  @Test
  public void addsStatusCode_badRequest() throws Exception {
    try {
      get("/badrequest");
    } catch (RuntimeException e) {
      // some servers think 400 is an error
    }

    Span span = takeSpan();
    assertThat(span.tags())
        .containsEntry("http.status_code", "400")
        .containsEntry("error", "400");
  }

  @Test
  public void reportsSpanOnException() throws Exception {
    reportsSpanOnException("/exception");
  }

  @Test
  public void reportsSpanOnException_async() throws Exception {
    reportsSpanOnException("/exceptionAsync");
  }

  Span reportsSpanOnException(String path) throws Exception {
    get(path);

    return takeSpan();
  }

  @Test
  public void addsErrorTagOnException() throws Exception {
    addsErrorTagOnException("/exception");
  }

  @Test
  public void addsErrorTagOnException_async() throws Exception {
    addsErrorTagOnException("/exceptionAsync");
  }

  @Test
  public void httpPathTagExcludesQueryParams() throws Exception {
    get("/foo?z=2&yAA=1");

    Span span = takeSpan();
    assertThat(span.tags())
        .containsEntry("http.path", "/foo");
  }

  protected Response get(String path) throws IOException {
    return get(new Request.Builder().url(url(path)).build());
  }

  protected Response get(Request request) throws IOException {
    try (Response response = client.newCall(request).execute()) {
      if (response.code() == 404) {
        throw new AssumptionViolatedException(request.url().encodedPath() + " not supported");
      }
      if (!HttpHeaders.hasBody(response)) return response;

      // buffer response so tests can read it. Otherwise the finally block will drop it
      ResponseBody toReturn;
      try (ResponseBody body = response.body()) {
        Buffer buffer = new Buffer();
        body.source().readAll(buffer);
        toReturn = ResponseBody.create(body.contentType(), body.contentLength(), buffer);
      }
      return response.newBuilder().body(toReturn).build();
    }
  }

  private void addsErrorTagOnException(String path) throws Exception {
    Span span = reportsSpanOnException(path);
    assertThat(span.tags())
        .containsKey("error");
  }
}
