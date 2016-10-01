package com.github.kristofa.brave.okhttp;

import com.github.kristofa.brave.AnnotationSubmitter;
import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.InheritableServerClientAndLocalSpanState;
import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.LocalTracer;
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
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Constants;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.TraceKeys;
import zipkin.internal.TraceUtil;
import zipkin.storage.InMemoryStorage;

import static com.github.kristofa.brave.http.BraveHttpHeaders.ParentSpanId;
import static com.github.kristofa.brave.http.BraveHttpHeaders.Sampled;
import static com.github.kristofa.brave.http.BraveHttpHeaders.SpanId;
import static com.github.kristofa.brave.http.BraveHttpHeaders.TraceId;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static zipkin.Constants.LOCAL_COMPONENT;
import static zipkin.TraceKeys.HTTP_STATUS_CODE;
import static zipkin.TraceKeys.HTTP_URL;

@RunWith(PowerMockRunner.class)
// tell mock not to mess with our rules or loggers!
@PowerMockIgnore({"okhttp3.*", "org.apache.logging.*", "com.sun.*"})
@PrepareForTest({AnnotationSubmitter.class, LocalTracer.class})
public class BraveTracingInterceptorTest {
  @Rule public ExpectedException thrown = ExpectedException.none();
  @Rule public MockWebServer server = new MockWebServer();

  Endpoint local = Endpoint.builder().serviceName("local").ipv4(127 << 24 | 1).port(100).build();
  Endpoint sa = local.toBuilder().serviceName("").port(server.getPort()).build();
  InMemoryStorage storage = new InMemoryStorage();

  OkHttpClient client;
  BraveTracingInterceptor interceptor;

  Span localSpan = Span.builder()
      .traceId(1L).id(1L).name("get")
      .timestamp(1000L).duration(4000L)
      .addBinaryAnnotation(BinaryAnnotation.create(LOCAL_COMPONENT, "okhttp", local))
      .build();

  Span clientSpan = Span.builder()
      .traceId(1L).parentId(1L).id(2L).name("get")
      .timestamp(3000L).duration(1000L)
      .addAnnotation(Annotation.create(3000, Constants.CLIENT_SEND, local))
      .addAnnotation(Annotation.create(4000, Constants.CLIENT_RECV, local))
      .addBinaryAnnotation(BinaryAnnotation.create(HTTP_URL, server.url("foo").toString(), local))
      .addBinaryAnnotation(BinaryAnnotation.address(Constants.SERVER_ADDR, sa))
      .build();

  @Before
  public void setup() throws Exception {
    PowerMockito.mockStatic(System.class);
    // Each call to nanoTime or currentTimeMillis increases the fake clock by 1 millisecond
    final AtomicLong clock = new AtomicLong();
    PowerMockito.when(System.currentTimeMillis())
        .then(i -> clock.addAndGet(1000000L) / 1000000L);
    PowerMockito.when(System.nanoTime())
        .then(i -> clock.addAndGet(1000000L));

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
    assertThat(headers.get(TraceId.getName())).isEqualTo(asList("1"));

    assertThat(headers).contains(
        entry(TraceId.getName(), asList("1")),
        entry(ParentSpanId.getName(), asList("1")),
        entry(SpanId.getName(), asList("2")),
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

    assertThat(collectedSpans()).containsExactly(clientSpan, localSpan);
  }

  @Test
  public void reportsToZipkin_addsCodeWhenNotOk() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(404));

    HttpUrl url = server.url("foo");
    client.newCall(new Request.Builder().url(url).build()).execute();

    assertThat(collectedSpans()).containsExactly(
        clientSpan.toBuilder()
            .addBinaryAnnotation(BinaryAnnotation.create(TraceKeys.HTTP_STATUS_CODE, "404", local))
            .build(), localSpan
    );
  }

  @Test
  public void reportsToZipkin_IncludesQueryParams() throws Exception {
    server.enqueue(new MockResponse());

    HttpUrl url = server.url("foo?z=2&yAA");
    client.newCall(new Request.Builder().url(url).build()).execute();

    assertThat(collectedSpans()).containsExactly(
        clientSpan.toBuilder().binaryAnnotations(asList(
            BinaryAnnotation.create(HTTP_URL, url.toString(), local),
            BinaryAnnotation.address(Constants.SERVER_ADDR, sa))
        ).build(), localSpan
    );
  }

  @Test
  public void tagIsApplicationSpanName() throws Exception {
    server.enqueue(new MockResponse());

    HttpUrl url = server.url("foo");
    client.newCall(new Request.Builder().url(url).tag("foo").build()).execute();

    assertThat(collectedSpans())
        .containsExactly(clientSpan, localSpan.toBuilder().name("foo").build());
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

    assertThat(collectedSpans().get(0).binaryAnnotations)
        .extracting(b -> tuple(b.key, new String(b.value)))
        .contains(tuple("http.user_agent", userAgent));
  }

  @Test
  public void reportsToZipkin_followupsAsNewSpans() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(408));
    server.enqueue(new MockResponse());

    HttpUrl url = server.url("foo");
    client.newCall(new Request.Builder().url(url).build()).execute();

    assertThat(collectedSpans()).containsOnly(
        clientSpan.toBuilder()
            .addBinaryAnnotation(BinaryAnnotation.create(HTTP_STATUS_CODE, "408", local))
            .build(),
        clientSpan.toBuilder()
            .id(3L).timestamp(6000L).annotations(asList(
            Annotation.create(6000, Constants.CLIENT_SEND, local),
            Annotation.create(7000, Constants.CLIENT_RECV, local)))
            .build(),
        localSpan.toBuilder().duration(7000L).build()
    );
  }

  BraveTracingInterceptor.Builder interceptorBuilder(Sampler sampler) {
    com.twitter.zipkin.gen.Endpoint localEndpoint = com.twitter.zipkin.gen.Endpoint.builder()
        .ipv4(local.ipv4)
        .ipv6(local.ipv6)
        .port(local.port)
        .serviceName(local.serviceName)
        .build();
    Brave brave = new Brave.Builder(new InheritableServerClientAndLocalSpanState(localEndpoint))
        .reporter(s -> storage.spanConsumer().accept(asList(s)))
        .traceSampler(sampler)
        .build();
    return BraveTracingInterceptor.builder(brave);
  }

  /** washes trace identifiers in the collected span */
  List<Span> collectedSpans() {
    List<Long> traceIds = storage.spanStore().traceIds();
    assertThat(traceIds).hasSize(1);
    return TraceUtil.washIds(storage.spanStore().getRawTrace(traceIds.get(0)));
  }

  /** washes propagated trace identifiers in the request headers */
  Map<String, List<String>> washIds(Map<String, List<String>> headers) {
    List<Long> traceIds = storage.spanStore().traceIds();
    assertThat(traceIds).hasSize(1);
    List<Span> unwashed = storage.spanStore().getRawTrace(traceIds.get(0));
    return TraceUtil.washIds(headers, unwashed);
  }
}
