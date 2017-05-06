package brave.http;

import brave.Tracing;
import brave.internal.HexCodec;
import brave.internal.StrictCurrentTraceContext;
import brave.propagation.CurrentTraceContext;
import brave.sampler.Sampler;
import java.util.Collections;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin.BinaryAnnotation;
import zipkin.Constants;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.TraceKeys;
import zipkin.internal.Util;
import zipkin.storage.InMemoryStorage;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

public abstract class ITHttpServer {

  @Rule public ExpectedException thrown = ExpectedException.none();
  public OkHttpClient client = new OkHttpClient();

  Endpoint local = Endpoint.builder().serviceName("local").ipv4(127 << 24 | 1).port(100).build();
  InMemoryStorage storage = new InMemoryStorage();

  protected CurrentTraceContext currentTraceContext = new StrictCurrentTraceContext();
  protected HttpTracing httpTracing;

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

    Request request = new Request.Builder().url(url(path))
        .header("X-B3-TraceId", traceId)
        .header("X-B3-ParentSpanId", parentId)
        .header("X-B3-SpanId", spanId)
        .header("X-B3-Sampled", "1")
        .build();

    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }

    assertThat(collectedSpans()).allSatisfy(s -> {
      assertThat(HexCodec.toLowerHex(s.traceId)).isEqualTo(traceId);
      assertThat(HexCodec.toLowerHex(s.parentId)).isEqualTo(parentId);
      assertThat(HexCodec.toLowerHex(s.id)).isEqualTo(spanId);
    });
  }

  @Test
  public void samplingDisabled() throws Exception {
    httpTracing = HttpTracing.create(tracingBuilder(Sampler.NEVER_SAMPLE).build());
    init();

    String path = "/foo";

    Request request = new Request.Builder().url(url(path)).build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }

    assertThat(collectedSpans())
        .isEmpty();
  }

  /**
   * Tests that the span propagates between under asynchronous callbacks (even if explicitly)
   */
  @Test
  public void async() throws Exception {
    String path = "/async";

    Request request = new Request.Builder().url(url(path)).build();
    try (Response response = client.newCall(request).execute()) {
      if (response.code() == 404) throw new AssumptionViolatedException(path + " not supported");
      assertThat(response.isSuccessful()).isTrue();
    } catch (AssumptionViolatedException e) {
      throw e;
    }

    List<Span> trace = collectedSpans();
    assertThat(trace).hasSize(1);
  }

  /**
   * This ensures thread-state is propagated from trace interceptors to user code. The endpoint
   * "/child" is expected to create a local span. When this works, it should be a child of the
   * "current span", in this case the span representing an incoming server request. When thread
   * state isn't managed properly, the child span will appear as a new trace.
   */
  @Test
  public void createsChildSpan() throws Exception {
    String path = "/child";

    Request request = new Request.Builder().url(url(path)).build();
    try (Response response = client.newCall(request).execute()) {
      if (response.code() == 404) throw new AssumptionViolatedException(path + " not supported");
    } catch (AssumptionViolatedException e) {
      throw e;
    } catch (Exception e) {
      // ok, but the span should include an error!
    }

    List<Span> trace = collectedSpans();
    assertThat(trace).hasSize(2);

    Span child = trace.get(0);
    Span parent = trace.get(1);

    assertThat(parent.traceId).isEqualTo(child.traceId);
    assertThat(parent.id).isEqualTo(child.parentId);
    assertThat(parent.timestamp).isLessThan(child.timestamp);
    assertThat(parent.duration).isGreaterThan(child.duration);
  }

  @Test
  public void reportsClientAddress() throws Exception {
    String path = "/foo";

    Request request = new Request.Builder().url(url(path)).build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }

    assertThat(collectedSpans())
        .flatExtracting(s -> s.binaryAnnotations)
        .extracting(b -> b.key)
        .contains(Constants.CLIENT_ADDR);
  }

  @Test
  public void reportsClientAddress_XForwardedFor() throws Exception {
    String path = "/foo";

    Request request = new Request.Builder().url(url(path))
        .header("X-Forwarded-For", "1.2.3.4")
        .build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }

    assertThat(collectedSpans())
        .flatExtracting(s -> s.binaryAnnotations)
        .extracting(b -> b.key, b -> b.endpoint.ipv4)
        .contains(tuple(Constants.CLIENT_ADDR, 1 << 24 | 2 << 16 | 3 << 8 | 4));
  }

  @Test
  public void reportsServerAnnotationsToZipkin() throws Exception {
    String path = "/foo";

    Request request = new Request.Builder().url(url(path)).build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }

    assertThat(collectedSpans())
        .flatExtracting(s -> s.annotations)
        .extracting(a -> a.value)
        .containsExactly("sr", "ss");
  }

  @Test
  public void defaultSpanNameIsMethodName() throws Exception {
    String path = "/foo";

    Request request = new Request.Builder().url(url(path)).build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }

    assertThat(collectedSpans())
        .extracting(s -> s.name)
        .containsExactly("get");
  }

  @Test
  public void supportsPortableCustomization() throws Exception {
    String uri = "/foo?z=2&yAA=1";

    httpTracing = httpTracing.toBuilder().serverParser(new HttpServerParser() {
      @Override public <Req> String spanName(HttpAdapter<Req, ?> adapter, Req req) {
        return adapter.method(req).toLowerCase() + " " + adapter.path(req);
      }

      @Override
      public <Req> void requestTags(HttpAdapter<Req, ?> adapter, Req req, brave.Span span) {
        span.tag(TraceKeys.HTTP_URL, adapter.url(req)); // just the path is logged by default
      }
    }).build();
    init();

    Request request = new Request.Builder().url(url(uri)).build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }

    List<Span> collectedSpans = collectedSpans();
    assertThat(collectedSpans)
        .extracting(s -> s.name)
        .containsExactly("get /foo");

    assertThat(collectedSpans)
        .flatExtracting(s -> s.binaryAnnotations)
        .filteredOn(b -> b.key.equals(TraceKeys.HTTP_URL))
        .extracting(b -> new String(b.value, Util.UTF_8))
        .containsExactly(url(uri));
  }

  @Test
  public void addsStatusCode_badRequest() throws Exception {
    String path = "/badrequest";

    Request request = new Request.Builder().url(url(path)).build();
    try (Response response = client.newCall(request).execute()) {
    } catch (RuntimeException e) {
      // some servers think 400 is an error
    }

    assertThat(collectedSpans())
        .flatExtracting(s -> s.binaryAnnotations)
        .contains(BinaryAnnotation.create(TraceKeys.HTTP_STATUS_CODE, "400", local));
  }

  @Test
  public void reportsSpanOnException() throws Exception {
    reportsSpanOnException("/exception");
  }

  @Test
  public void reportsSpanOnException_async() throws Exception {
    reportsSpanOnException("/exceptionAsync");
  }

  private void reportsSpanOnException(String path) {
    Request request = new Request.Builder().url(url(path)).build();
    try (Response response = client.newCall(request).execute()) {
      if (response.code() == 404) throw new AssumptionViolatedException(path + " not supported");
    } catch (AssumptionViolatedException e) {
      throw e;
    } catch (Exception e) {
      // ok, but the span should include an error!
    }

    assertThat(collectedSpans()).hasSize(1);
  }

  @Test
  public void addsErrorTagOnException() throws Exception {
    addsErrorTagOnException("/exception");
  }

  @Test
  public void addsErrorTagOnException_async() throws Exception {
    addsErrorTagOnException("/exceptionAsync");
  }

  private void addsErrorTagOnException(String path) {
    reportsSpanOnException(path);
    try {
      Thread.sleep(1000L);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    assertThat(collectedSpans())
        .flatExtracting(s -> s.binaryAnnotations)
        .extracting(b -> b.key)
        .contains(Constants.ERROR);
  }

  @Test
  public void httpPathTagExcludesQueryParams() throws Exception {
    String uri = "/foo?z=2&yAA=1";

    Request request = new Request.Builder().url(url(uri)).build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }

    assertThat(collectedSpans())
        .flatExtracting(s -> s.binaryAnnotations)
        .filteredOn(b -> b.key.equals(TraceKeys.HTTP_PATH))
        .extracting(b -> new String(b.value, Util.UTF_8))
        .containsExactly("/foo");
  }

  Tracing.Builder tracingBuilder(Sampler sampler) {
    return Tracing.newBuilder()
        .reporter(s -> storage.spanConsumer().accept(asList(s)))
        .currentTraceContext(currentTraceContext)
        .localEndpoint(local)
        .sampler(sampler);
  }

  protected List<Span> collectedSpans() {
    List<List<Span>> result = storage.spanStore().getRawTraces();
    if (result.isEmpty()) return Collections.emptyList();
    assertThat(result).hasSize(1);
    return result.get(0);
  }
}
