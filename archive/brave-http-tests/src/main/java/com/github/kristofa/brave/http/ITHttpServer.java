package com.github.kristofa.brave.http;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.InheritableServerClientAndLocalSpanState;
import com.github.kristofa.brave.Sampler;
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
import zipkin2.Span;
import zipkin.TraceKeys;
import zipkin2.storage.InMemoryStorage;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.groups.Tuple.tuple;

public abstract class ITHttpServer {

  @Rule public ExpectedException thrown = ExpectedException.none();
  public OkHttpClient client = new OkHttpClient();

  Endpoint local = Endpoint.builder().serviceName("local").ipv4(127 << 24 | 1).port(100).build();
  InMemoryStorage storage = InMemoryStorage.newBuilder().build();

  protected Brave brave;

  @Before
  public void setup() throws Exception {
    init(brave = braveBuilder(Sampler.ALWAYS_SAMPLE).build());
  }

  void init(Brave brave) throws Exception {
    init(brave, new DefaultSpanNameProvider());
  }

  /** recreate the server if needed */
  protected abstract void init(Brave brave, SpanNameProvider spanNameProvider) throws Exception;

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
      assertThat(s.traceId()).isEqualTo(traceId);
      assertThat(s.parentId()).isEqualTo(parentId);
      assertThat(s.id()).isEqualTo(spanId);
    });
  }

  @Test
  public void samplingDisabled() throws Exception {
    init(brave = braveBuilder(Sampler.NEVER_SAMPLE).build());

    String path = "/foo";

    Request request = new Request.Builder().url(url(path)).build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }

    assertThat(collectedSpans())
        .isEmpty();
  }


  @Test
  public void createsChildSpan() throws Exception {
    createsChildSpan("/child");
  }

  @Test
  public void createsChildSpan_async() throws Exception {
    createsChildSpan("/childAsync");
  }

  /**
   * This ensures thread-state is propagated from trace interceptors to user code. The endpoint
   * "/child" is expected to create a local span. When this works, it should be a child of the
   * "current span", in this case the span representing an incoming server request. When thread
   * state isn't managed properly, the child span will appear as a new trace.
   */
  private void createsChildSpan(String path) {
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

    assertThat(parent.traceId()).isEqualTo(child.traceId());
    assertThat(parent.id()).isEqualTo(child.parentId());
    assertThat(parent.timestamp()).isLessThan(child.timestamp());
    assertThat(parent.duration()).isGreaterThan(child.duration());
  }

  @Test
  public void reportsClientAddress() throws Exception {
    String path = "/foo";

    Request request = new Request.Builder().url(url(path)).build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }

    assertThat(collectedSpans())
        .flatExtracting(Span::remoteEndpoint)
        .isNotEmpty();
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
        .extracting(s -> s.remoteEndpoint().ipv4())
        .contains("1.2.3.4");
  }

  @Test
  public void reportsServerAnnotationsToZipkin() throws Exception {
    String path = "/foo";

    Request request = new Request.Builder().url(url(path)).build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }

    assertThat(collectedSpans())
        .extracting(Span::kind)
        .containsOnly(Span.Kind.SERVER);
  }

  @Test
  public void defaultSpanNameIsMethodName() throws Exception {
    String path = "/foo";

    Request request = new Request.Builder().url(url(path)).build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }

    assertThat(collectedSpans())
        .extracting(Span::name)
        .containsExactly("get");
  }

  @Test
  public void supportsSpanNameProvider() throws Exception {
    init(brave, r -> r.getUri().getPath());
    String path = "/foo";

    Request request = new Request.Builder().url(url(path)).build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }

    assertThat(collectedSpans())
        .extracting(Span::name)
        .containsExactly(path);
  }

  @Test
  public void addsStatusCodeWhenNotOk() throws Exception {
    String path = "/notfound";

    Request request = new Request.Builder().url(url(path)).build();
    try (Response response = client.newCall(request).execute()) {
    } catch (RuntimeException e) {
      // some servers think 404 is an error
    }

    assertThat(collectedSpans())
        .flatExtracting(s -> s.tags().entrySet())
        .contains(entry(TraceKeys.HTTP_STATUS_CODE, "404"));
  }

  @Test
  public void reportsSpanOnTransportException() throws Exception {
    reportsSpanOnTransportException("/disconnect");
  }

  @Test
  public void reportsSpanOnTransportException_async() throws Exception {
    reportsSpanOnTransportException("/disconnectAsync");
  }

  private void reportsSpanOnTransportException(String path) {
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
  public void addsErrorTagOnTransportException() throws Exception {
    addsErrorTagOnTransportException("/disconnect");
  }

  @Test
  public void addsErrorTagOnTransportException_async() throws Exception {
    addsErrorTagOnTransportException("/disconnectAsync");
  }

  private void addsErrorTagOnTransportException(String path) {
    reportsSpanOnTransportException(path);

    assertThat(collectedSpans())
        .flatExtracting(s -> s.tags().keySet())
        .contains(Constants.ERROR);
  }

  @Test
  public void httpUrlTagIncludesQueryParams() throws Exception {
    String path = "/foo?z=2&yAA=1";

    Request request = new Request.Builder().url(url(path)).build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }

    assertThat(collectedSpans())
        .flatExtracting(s -> s.tags().entrySet())
        .contains(entry(TraceKeys.HTTP_URL, url(path).toString()));
  }

  Brave.Builder braveBuilder(Sampler sampler) {
    com.twitter.zipkin.gen.Endpoint localEndpoint = com.twitter.zipkin.gen.Endpoint.builder()
        .ipv4(local.ipv4)
        .ipv6(local.ipv6)
        .port(local.port)
        .serviceName(local.serviceName)
        .build();
    return new Brave.Builder(new InheritableServerClientAndLocalSpanState(localEndpoint))
        .spanReporter(s -> storage.spanConsumer().accept(asList(s)))
        .traceSampler(sampler);
  }

  List<Span> collectedSpans() {
    List<List<Span>> result = storage.spanStore().getTraces();
    if (result.isEmpty()) return Collections.emptyList();
    assertThat(result).hasSize(1);
    return result.get(0);
  }
}
