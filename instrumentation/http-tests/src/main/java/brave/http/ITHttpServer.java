package brave.http;

import brave.SpanCustomizer;
import brave.internal.HexCodec;
import brave.sampler.Sampler;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Test;
import zipkin.Constants;
import zipkin.Span;
import zipkin.TraceKeys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

public abstract class ITHttpServer extends ITHttp {
  OkHttpClient client = new OkHttpClient();

  @Before
  public void setup() throws Exception {
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

    assertThat(spans).allSatisfy(s -> {
      assertThat(HexCodec.toLowerHex(s.traceId)).isEqualTo(traceId);
      assertThat(HexCodec.toLowerHex(s.parentId)).isEqualTo(parentId);
      assertThat(HexCodec.toLowerHex(s.id)).isEqualTo(spanId);
    });
  }

  @Test
  public void samplingDisabled() throws Exception {
    httpTracing = HttpTracing.create(tracingBuilder(Sampler.NEVER_SAMPLE).build());
    init();

    get("/foo");

    assertThat(spans)
        .isEmpty();
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

    assertThat(spans)
        .isEmpty();
  }

  /**
   * Tests that the span propagates between under asynchronous callbacks (even if explicitly)
   */
  @Test
  public void async() throws Exception {
    get("/async");

    assertThat(spans).hasSize(1);
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

    assertThat(spans).hasSize(2);

    Span child = spans.pop();
    Span parent = spans.pop();

    assertThat(parent.traceId).isEqualTo(child.traceId);
    assertThat(parent.id).isEqualTo(child.parentId);
    assertThat(parent.timestamp).isLessThan(child.timestamp);
    assertThat(parent.duration).isGreaterThan(child.duration);
  }

  @Test
  public void reportsClientAddress() throws Exception {
    get("/foo");

    assertThat(spans)
        .flatExtracting(s -> s.binaryAnnotations)
        .extracting(b -> b.key)
        .contains(Constants.CLIENT_ADDR);
  }

  @Test
  public void reportsClientAddress_XForwardedFor() throws Exception {
    get(new Request.Builder().url(url("/foo"))
        .header("X-Forwarded-For", "1.2.3.4")
        .build());

    assertThat(spans)
        .flatExtracting(s -> s.binaryAnnotations)
        .extracting(b -> b.key, b -> b.endpoint.ipv4)
        .contains(tuple(Constants.CLIENT_ADDR, 1 << 24 | 2 << 16 | 3 << 8 | 4));
  }

  @Test
  public void reportsServerAnnotationsToZipkin() throws Exception {
    get("/foo");

    assertThat(spans)
        .flatExtracting(s -> s.annotations)
        .extracting(a -> a.value)
        .containsExactly("sr", "ss");
  }

  @Test
  public void defaultSpanNameIsMethodName() throws Exception {
    get("/foo");

    assertThat(spans)
        .extracting(s -> s.name)
        .containsExactly("get");
  }

  @Test
  public void supportsPortableCustomization() throws Exception {
    httpTracing = httpTracing.toBuilder().serverParser(new HttpServerParser() {
      @Override
      public <Req> void request(HttpAdapter<Req, ?> adapter, Req req, SpanCustomizer customizer) {
        customizer.name(adapter.method(req).toLowerCase() + " " + adapter.path(req));
        customizer.tag(TraceKeys.HTTP_URL, adapter.url(req)); // just the path is logged by default
      }
    }).build();
    init();

    String uri = "/foo?z=2&yAA=1";
    get(uri);

    assertThat(spans)
        .extracting(s -> s.name)
        .containsExactly("get /foo");

    assertReportedTagsInclude(TraceKeys.HTTP_URL, url(uri));
  }

  @Test
  public void addsStatusCode_badRequest() throws Exception {
    try {
      get("/badrequest");
    } catch (RuntimeException e) {
      // some servers think 400 is an error
    }

    assertReportedTagsInclude(TraceKeys.HTTP_STATUS_CODE, "400");
    assertReportedTagsInclude(Constants.ERROR, "400");
  }

  @Test
  public void reportsSpanOnException() throws Exception {
    reportsSpanOnException("/exception");
  }

  @Test
  public void reportsSpanOnException_async() throws Exception {
    reportsSpanOnException("/exceptionAsync");
  }

  private void reportsSpanOnException(String path) throws IOException {
    get(path);

    assertThat(spans).hasSize(1);
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

    assertReportedTagsInclude(TraceKeys.HTTP_PATH, "/foo");
  }

  protected Response get(String path) throws IOException {
    return get(new Request.Builder().url(url(path)).build());
  }

  protected Response get(Request request) throws IOException {
    try (Response response = client.newCall(request).execute()) {
      if (response.code() == 404) {
        throw new AssumptionViolatedException(request.url().encodedPath() + " not supported");
      }
      return response;
    }
  }

  private void addsErrorTagOnException(String path) throws IOException {
    reportsSpanOnException(path);
    try {
      Thread.sleep(1000L);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    assertThat(spans)
        .flatExtracting(s -> s.binaryAnnotations)
        .extracting(b -> b.key)
        .contains(Constants.ERROR);
  }
}
