package com.github.kristofa.brave.okhttp;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.InheritableServerClientAndLocalSpanState;
import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.Sampler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin.Endpoint;
import zipkin.TraceKeys;
import zipkin.internal.TraceUtil;
import zipkin2.Span;
import zipkin2.storage.InMemoryStorage;

import static com.github.kristofa.brave.http.BraveHttpHeaders.ParentSpanId;
import static com.github.kristofa.brave.http.BraveHttpHeaders.Sampled;
import static com.github.kristofa.brave.http.BraveHttpHeaders.SpanId;
import static com.github.kristofa.brave.http.BraveHttpHeaders.TraceId;
import static com.github.kristofa.brave.okhttp.BraveTracingInterceptor.addTraceHeaders;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

public class BraveTracingInterceptorTest {
  @Rule public ExpectedException thrown = ExpectedException.none();
  @Rule public MockWebServer server = new MockWebServer();

  Endpoint local = Endpoint.builder().serviceName("local").ipv4(127 << 24 | 1).port(100).build();
  InMemoryStorage storage = InMemoryStorage.newBuilder().build();

  OkHttpClient client;
  BraveTracingInterceptor interceptor;

  @Before
  public void setup() throws Exception {
    interceptor = interceptorBuilder(Sampler.ALWAYS_SAMPLE).build();
    client = new OkHttpClient.Builder()
        .addInterceptor(interceptor)
        .addNetworkInterceptor(interceptor).build();
  }

  @After
  public void close() {
    client.dispatcher().executorService().shutdownNow();
  }

  @Test
  public void propagatesSpan() throws Exception {
    server.enqueue(new MockResponse());

    client.newCall(new Request.Builder().url(server.url("foo")).build()).execute();

    RecordedRequest request = server.takeRequest();
    Map<String, List<String>> headers = washIds(request.getHeaders().toMultimap());

    assertThat(headers).contains(
        entry(TraceId.getName(), asList("0000000000000001")),
        entry(ParentSpanId.getName(), asList("0000000000000001")),
        entry(SpanId.getName(), asList("0000000000000002")),
        entry(Sampled.getName(), asList("1"))
    );
  }

  @Test
  public void propagates_sampledFalse() throws Exception {
    interceptor = interceptorBuilder(Sampler.NEVER_SAMPLE).build();
    client = new OkHttpClient.Builder().
        addInterceptor(interceptor).addNetworkInterceptor(interceptor).build();

    server.enqueue(new MockResponse());
    client.newCall(new Request.Builder().url(server.url("foo")).build()).execute();

    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeaders().toMultimap()).contains(
        entry(Sampled.getName(), asList("0"))
    ).doesNotContainKeys(TraceId.getName(), SpanId.getName(), ParentSpanId.getName());
  }

  @Test
  public void reportsToZipkin() throws Exception {
    server.enqueue(new MockResponse());

    HttpUrl url = server.url("foo");
    client.newCall(new Request.Builder().url(url).build()).execute();

    String one = "0000000000000001", two = "0000000000000002";
    assertThat(collectedSpans())
        .extracting(Span::traceId, Span::parentId, Span::id, Span::name)
        .containsExactly(
            tuple(one, one, two, "get"),
            tuple(one, null, one, "get")
        );
  }

  @Test
  public void reportsToZipkin_addsCodeWhenNotOk() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(404));

    HttpUrl url = server.url("foo");
    client.newCall(new Request.Builder().url(url).build()).execute();

    assertThat(collectedSpans())
        .flatExtracting(s -> s.tags().entrySet())
        .contains(entry(TraceKeys.HTTP_STATUS_CODE, "404"));
  }

  @Test
  public void reportsToZipkin_IncludesQueryParams() throws Exception {
    server.enqueue(new MockResponse());

    HttpUrl url = server.url("foo?z=2&yAA");
    client.newCall(new Request.Builder().url(url).build()).execute();

    assertThat(collectedSpans())
        .flatExtracting(s -> s.tags().entrySet())
        .contains(entry(TraceKeys.HTTP_URL, url.toString()));
  }

  @Test
  public void tagIsApplicationSpanName() throws Exception {
    server.enqueue(new MockResponse());

    HttpUrl url = server.url("foo");
    client.newCall(new Request.Builder().url(url).tag("foo").build()).execute();

    assertThat(collectedSpans()).extracting(Span::name)
        .containsExactly("get", "foo");
  }

  @Test
  public void addMoreTags() throws Exception {
    close();
    interceptor = interceptorBuilder(Sampler.ALWAYS_SAMPLE).parser(new OkHttpParser() {
      @Override
      public List<KeyValueAnnotation> networkRequestTags(Request request) {
        List<KeyValueAnnotation> result = new ArrayList<>();
        result.addAll(super.networkRequestTags(request));
        String userAgent = request.header("User-Agent");
        if (userAgent != null) {
          result.add(KeyValueAnnotation.create("http.user_agent", userAgent));
        }
        return result;
      }
    }).build();
    client = new OkHttpClient.Builder()
        .addInterceptor(interceptor).addNetworkInterceptor(interceptor).build();

    server.enqueue(new MockResponse());

    HttpUrl url = server.url("foo");

    String userAgent = "Microservice Client v2.0";
    client.newCall(new Request.Builder().url(url)
        .header("User-Agent", userAgent).build()).execute();

    assertThat(collectedSpans())
        .flatExtracting(s -> s.tags().entrySet())
        .contains(entry("http.user_agent", userAgent));
  }

  @Test
  public void reportsToZipkin_followupsAsNewSpans() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(408));
    server.enqueue(new MockResponse());

    HttpUrl url = server.url("foo");
    client.newCall(new Request.Builder().url(url).build()).execute();

    String one = "0000000000000001", two = "0000000000000002", three = "0000000000000003";
    assertThat(collectedSpans())
        .extracting(Span::traceId, Span::parentId, Span::id, Span::name)
        .containsExactly(
            tuple(one, one, two, "get"),
            tuple(one, one, three, "get"),
            tuple(one, null, one, "get")
        );
  }

  @Test
  public void addTraceHeaders_128() {
    com.github.kristofa.brave.SpanId id = com.github.kristofa.brave.SpanId.builder()
        .traceIdHigh(1).traceId(2).spanId(3).parentId(2L).build();

    Request original = new Request.Builder().url("http://localhost").build();

    assertThat(addTraceHeaders(original, id).build().header(TraceId.getName()))
        .isEqualTo("00000000000000010000000000000002");
  }

  BraveTracingInterceptor.Builder interceptorBuilder(Sampler sampler) {
    com.twitter.zipkin.gen.Endpoint localEndpoint = com.twitter.zipkin.gen.Endpoint.builder()
        .ipv4(local.ipv4)
        .ipv6(local.ipv6)
        .port(local.port)
        .serviceName(local.serviceName)
        .build();
    // Each call increases the fake clock by 1 millisecond
    final AtomicLong clock = new AtomicLong();
    Brave brave = new Brave.Builder(new InheritableServerClientAndLocalSpanState(localEndpoint))
        .spanReporter(s -> storage.spanConsumer().accept(asList(s)))
        .clock(() -> clock.addAndGet(1000L))
        .traceSampler(sampler)
        .build();
    return BraveTracingInterceptor.builder(brave);
  }

  /** washes trace identifiers in the collected span */
  List<Span> collectedSpans() {
    List<List<Span>> traces = storage.spanStore().getTraces();
    assertThat(traces).hasSize(1);
    return TraceUtil.washIds(traces.get(0));
  }

  /** washes propagated trace identifiers in the request headers */
  Map<String, List<String>> washIds(Map<String, List<String>> headers) {
    List<List<Span>> traces = storage.spanStore().getTraces();
    assertThat(traces).hasSize(1);
    return TraceUtil.washIds(headers, traces.get(0));
  }
}
