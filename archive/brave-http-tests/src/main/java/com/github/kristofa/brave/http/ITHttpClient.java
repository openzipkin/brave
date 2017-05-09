package com.github.kristofa.brave.http;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.InheritableServerClientAndLocalSpanState;
import com.github.kristofa.brave.Sampler;
import com.github.kristofa.brave.ServerSpan;
import com.github.kristofa.brave.SpanId;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.After;
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

public abstract class ITHttpClient<C> {
  @Rule public ExpectedException thrown = ExpectedException.none();
  @Rule public MockWebServer server = new MockWebServer();

  Endpoint local = Endpoint.builder().serviceName("local").ipv4(127 << 24 | 1).port(100).build();
  InMemoryStorage storage = new InMemoryStorage();

  protected Brave brave;
  C client;

  @Before
  public void setup() {
    brave = braveBuilder(Sampler.ALWAYS_SAMPLE).build();
    client = newClient(server.getPort());
  }

  /**
   * Make sure the client you return has retries disabled.
   */
  protected abstract C newClient(int port);

  protected abstract C newClient(int port, SpanNameProvider spanNameProvider);

  protected abstract void closeClient(C client) throws IOException;

  protected abstract void get(C client, String pathIncludingQuery) throws Exception;

  protected abstract void getAsync(C client, String pathIncludingQuery) throws Exception;

  @After
  public void close() throws IOException {
    closeClient(client);
  }

  @Test
  public void propagatesSpan() throws Exception {
    server.enqueue(new MockResponse());
    get(client, "/foo");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeaders().toMultimap())
        .containsKeys("x-b3-traceId", "x-b3-spanId")
        .containsEntry("x-b3-sampled", asList("1"));
  }

  @Test
  public void usesExistingTraceId_server() throws Exception {
    server.enqueue(new MockResponse());

    brave.serverTracer().setStateUnknown("test");
    ServerSpan parent = brave.serverSpanThreadBinder().getCurrentServerSpan();
    try {
      get(client, "/foo");
    } finally {
      brave.serverTracer().clearCurrentSpan();
    }

    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeader("x-b3-traceId"))
        .isEqualTo(Util.toLowerHex(parent.getSpan().getTrace_id()));
    assertThat(request.getHeader("x-b3-parentspanid"))
        .isEqualTo(IdConversion.convertToString(parent.getSpan().getId()));
  }

  @Test
  public void usesExistingTraceId_local() throws Exception {
    server.enqueue(new MockResponse());

    SpanId parent = brave.localTracer().startNewSpan(getClass().getSimpleName(), "test");
    try {
      get(client, "/foo");
    } finally {
      brave.localTracer().finishSpan();
    }

    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeader("x-b3-traceId"))
        .isEqualTo(parent.traceIdString());
    assertThat(request.getHeader("x-b3-parentspanid"))
        .isEqualTo(IdConversion.convertToString(parent.spanId));
  }

  /**
   * This tests that the parent is determined at the time the request was made, not when the request
   * was executed.
   */
  @Test
  public void usesParentFromInvocationTime_local() throws Exception {
    server.enqueue(new MockResponse().setBodyDelay(1, TimeUnit.SECONDS));
    server.enqueue(new MockResponse());

    SpanId parent = brave.localTracer().startNewSpan(getClass().getSimpleName(), "test");
    try {
      getAsync(client, "/foo");
      getAsync(client, "/foo");
    } finally {
      brave.localTracer().finishSpan();
    }

    // changing the local span after the fact!
    brave.localTracer().startNewSpan(getClass().getSimpleName(), "test");

    try {
      for (int i = 0; i < 2; i++) {
        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("x-b3-traceId"))
            .isEqualTo(parent.traceIdString());
        assertThat(request.getHeader("x-b3-parentspanid"))
            .isEqualTo(IdConversion.convertToString(parent.spanId));
      }
    } finally {
      brave.localTracer().finishSpan();
    }
  }

  @Test
  public void usesParentFromInvocationTime_server() throws Exception {
    server.enqueue(new MockResponse().setBodyDelay(1, TimeUnit.SECONDS));
    server.enqueue(new MockResponse());

    brave.serverTracer().setStateUnknown("test");
    ServerSpan parent = brave.serverSpanThreadBinder().getCurrentServerSpan();
    try {
      getAsync(client, "/foo");
      getAsync(client, "/foo");
    } finally {
      brave.serverTracer().clearCurrentSpan();
    }

    // changing the server span after the fact!
    brave.serverTracer().setStateUnknown("test");

    try {
      for (int i = 0; i < 2; i++) {
        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("x-b3-traceId"))
            .isEqualTo(Util.toLowerHex(parent.getSpan().getTrace_id()));
        assertThat(request.getHeader("x-b3-parentspanid"))
            .isEqualTo(IdConversion.convertToString(parent.getSpan().getId()));
      }
    } finally {
      brave.serverTracer().clearCurrentSpan();
    }
  }

  @Test
  public void propagates_sampledFalse() throws Exception {
    brave = braveBuilder(Sampler.NEVER_SAMPLE).build();
    close();
    client = newClient(server.getPort());

    server.enqueue(new MockResponse());
    get(client, "/foo");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeaders().toMultimap())
        .doesNotContainKeys("x-b3-traceId", "x-b3-parentSpanId", "x-b3-spanId")
        .containsEntry("x-b3-sampled", asList("0"));
  }

  @Test
  public void reportsClientAnnotationsToZipkin() throws Exception {
    server.enqueue(new MockResponse());
    get(client, "/foo");

    assertThat(collectedSpans())
        .flatExtracting(s -> s.annotations)
        .extracting(a -> a.value)
        .containsExactly("cs", "cr");
  }

  @Test
  public void defaultSpanNameIsMethodName() throws Exception {
    server.enqueue(new MockResponse());
    get(client, "/foo");

    assertThat(collectedSpans())
        .extracting(s -> s.name)
        .containsExactly("get");
  }

  @Test
  public void supportsSpanNameProvider() throws Exception {
    close();
    client = newClient(server.getPort(), r -> r.getUri().getPath());

    server.enqueue(new MockResponse());
    get(client, "/foo");

    assertThat(collectedSpans())
        .extracting(s -> s.name)
        .containsExactly("/foo");
  }

  @Test
  public void addsStatusCodeWhenNotOk() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(404));

    try {
      get(client, "/foo");
    } catch (RuntimeException e) {
      // some clients think 404 is an error
    }

    assertThat(collectedSpans())
        .flatExtracting(s -> s.binaryAnnotations)
        .contains(BinaryAnnotation.create(TraceKeys.HTTP_STATUS_CODE, "404", local));
  }

  @Test
  public void reportsSpanOnTransportException() throws Exception {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

    try {
      get(client, "/foo");
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

    server.enqueue(new MockResponse());
    get(client, path);

    assertThat(collectedSpans())
        .flatExtracting(s -> s.binaryAnnotations)
        .filteredOn(b -> b.key.equals(TraceKeys.HTTP_URL))
        .extracting(b -> new String(b.value, Util.UTF_8))
        .containsExactly(server.url(path).toString());
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
    assertThat(result).hasSize(1);
    return result.get(0);
  }
}
