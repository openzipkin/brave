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

public abstract class ITHttpServer {
  @Rule public ExpectedException thrown = ExpectedException.none();
  public OkHttpClient client = new OkHttpClient();

  Endpoint local = Endpoint.builder().serviceName("local").ipv4(127 << 24 | 1).port(100).build();
  InMemoryStorage storage = new InMemoryStorage();

  protected Brave brave;

  @Before
  public void setup() throws Exception {
    init(brave = braveBuilder(Sampler.ALWAYS_SAMPLE).build());
  }

  /** recreate the server if needed */
  protected abstract void init(Brave brave) throws Exception;

  /** recreate the server if needed */
  protected abstract void init(Brave brave, SpanNameProvider spanNameProvider) throws Exception;

  protected abstract String baseUrl(String path);

  @Test
  public void usesExistingTraceId() throws Exception {
    String path = "/foo";

    final String traceId = "463ac35c9f6413ad";
    final String parentId = traceId;
    final String spanId = "48485a3953bb6124";

    Request request = new Request.Builder().url(baseUrl(path))
        .header("X-B3-TraceId", traceId)
        .header("X-B3-ParentSpanId", parentId)
        .header("X-B3-SpanId", spanId)
        .header("X-B3-Sampled", "1")
        .build();

    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }

    assertThat(collectedSpans()).allSatisfy(s -> {
      assertThat(IdConversion.convertToString(s.traceId)).isEqualTo(traceId);
      assertThat(IdConversion.convertToString(s.parentId)).isEqualTo(parentId);
      assertThat(IdConversion.convertToString(s.id)).isEqualTo(spanId);
    });
  }

  @Test
  public void samplingDisabled() throws Exception {
    init(brave = braveBuilder(Sampler.NEVER_SAMPLE).build());

    String path = "/foo";

    Request request = new Request.Builder().url(baseUrl(path)).build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }

    assertThat(collectedSpans())
        .isEmpty();
  }

  @Test
  public void reportsServerAnnotationsToZipkin() throws Exception {
    String path = "/foo";

    Request request = new Request.Builder().url(baseUrl(path)).build();
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

    Request request = new Request.Builder().url(baseUrl(path)).build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }

    assertThat(collectedSpans())
        .extracting(s -> s.name)
        .containsExactly("get");
  }

  @Test
  public void supportsSpanNameProvider() throws Exception {
    init(brave, r -> r.getUri().getPath());
    String path = "/foo";

    Request request = new Request.Builder().url(baseUrl(path)).build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }

    assertThat(collectedSpans())
        .extracting(s -> s.name)
        .containsExactly(path);
  }

  @Test
  public void addsStatusCodeWhenNotOk() throws Exception {
    String path = "/notfound";

    Request request = new Request.Builder().url(baseUrl(path)).build();
    try (Response response = client.newCall(request).execute()) {
    } catch (RuntimeException e) {
      // some servers think 404 is an error
    }

    assertThat(collectedSpans())
        .flatExtracting(s -> s.binaryAnnotations)
        .contains(BinaryAnnotation.create(TraceKeys.HTTP_STATUS_CODE, "404", local));
  }

  @Test
  public void reportsSpanOnTransportException() throws Exception {
    String path = "/disconnect";

    Request request = new Request.Builder().url(baseUrl(path)).build();
    try (Response response = client.newCall(request).execute()) {
    } catch (Exception e) {
      // ok, but the span should include an error!
    }

    assertThat(collectedSpans()).hasSize(1);
  }

  @Test
  public void addsErrorTagOnTransportException() throws Exception {
    reportsSpanOnTransportException();

    assertThat(collectedSpans())
        .flatExtracting(s -> s.binaryAnnotations)
        .extracting(b -> b.key)
        .contains(Constants.ERROR);
  }

  @Test
  public void httpUrlTagIncludesQueryParams() throws Exception {
    String path = "/foo?z=2&yAA=1";

    Request request = new Request.Builder().url(baseUrl(path)).build();
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
    }

    assertThat(collectedSpans())
        .flatExtracting(s -> s.binaryAnnotations)
        .filteredOn(b -> b.key.equals(TraceKeys.HTTP_URL))
        .extracting(b -> new String(b.value, Util.UTF_8))
        .containsExactly(baseUrl(path).toString());
  }

  Brave.Builder braveBuilder(Sampler sampler) {
    com.twitter.zipkin.gen.Endpoint localEndpoint = com.twitter.zipkin.gen.Endpoint.builder()
        .ipv4(local.ipv4)
        .ipv6(local.ipv6)
        .port(local.port)
        .serviceName(local.serviceName)
        .build();
    return new Brave.Builder(new InheritableServerClientAndLocalSpanState(localEndpoint))
        .reporter(s -> storage.spanConsumer().accept(asList(s)))
        .traceSampler(sampler);
  }

  List<Span> collectedSpans() {
    List<List<Span>> result = storage.spanStore().getRawTraces();
    if (result.isEmpty()) return Collections.emptyList();
    assertThat(result).hasSize(1);
    return result.get(0);
  }
}
