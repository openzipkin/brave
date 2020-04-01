/*
 * Copyright 2013-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.test.http;

import brave.Clock;
import brave.SpanCustomizer;
import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.http.HttpAdapter;
import brave.http.HttpClientParser;
import brave.http.HttpRequest;
import brave.http.HttpResponseParser;
import brave.http.HttpRuleSampler;
import brave.http.HttpTags;
import brave.http.HttpTracing;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.Propagation;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunction;
import brave.test.ITRemote;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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

import static brave.http.HttpRequestMatchers.pathStartsWith;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class ITHttpClient<C> extends ITRemote {
  @Rule public MockWebServer server = new MockWebServer();

  protected C client;
  protected HttpTracing httpTracing = HttpTracing.create(tracing);
  protected Extractor<RecordedRequest> extractor =
    propagationFactory.create(Propagation.KeyFactory.STRING).extractor(RecordedRequest::getHeader);

  @Before public void setup() throws IOException {
    client = newClient(server.getPort());
  }

  /** Make sure the client you return has retries disabled. */
  protected abstract C newClient(int port) throws IOException;

  protected abstract void closeClient(C client) throws IOException;

  protected abstract void get(C client, String pathIncludingQuery) throws IOException;

  protected abstract void post(C client, String pathIncludingQuery, String body) throws IOException;

  /** Closes the client prior to calling {@link ITRemote#close()} */
  @Override @After public void close() throws Exception {
    closeClient(client);
    super.close();
  }

  @Test public void propagatesNewTrace() throws Exception {
    server.enqueue(new MockResponse());
    get(client, "/foo");

    TraceContext extracted = extract(takeRequest());
    assertThat(extracted.sampled()).isTrue();
    assertThat(extracted.parentIdString()).isNull();
    assertSameIds(reporter.takeRemoteSpan(Span.Kind.CLIENT), extracted);
  }

  @Test public void propagatesChildOfCurrentSpan() throws IOException {
    server.enqueue(new MockResponse());

    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      get(client, "/foo");
    }

    TraceContext extracted = extract(takeRequest());
    assertThat(extracted.sampled()).isTrue();
    assertChildOf(extracted, parent);
    assertSameIds(reporter.takeRemoteSpan(Span.Kind.CLIENT), extracted);
  }

  /** Unlike Brave 3, Brave 4 propagates trace ids even when unsampled */
  @Test public void propagatesUnsampledContext() throws IOException {
    server.enqueue(new MockResponse());

    TraceContext parent = newTraceContext(SamplingFlags.NOT_SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      get(client, "/foo");
    }

    TraceContext extracted = extract(takeRequest());
    assertThat(extracted.sampled()).isFalse();
    assertChildOf(extracted, parent);
  }

  @Test public void propagatesBaggage() throws IOException {
    server.enqueue(new MockResponse());

    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      BAGGAGE_FIELD.updateValue(parent, "joey");
      get(client, "/foo");
    }

    TraceContext extracted = extract(takeRequest());
    assertThat(BAGGAGE_FIELD.getValue(extracted)).isEqualTo("joey");

    reporter.takeRemoteSpan(Span.Kind.CLIENT);
  }

  @Test public void propagatesBaggage_unsampled() throws IOException {
    server.enqueue(new MockResponse());

    TraceContext parent = newTraceContext(SamplingFlags.NOT_SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      BAGGAGE_FIELD.updateValue(parent, "joey");
      get(client, "/foo");
    }

    TraceContext extracted = extract(takeRequest());
    assertThat(BAGGAGE_FIELD.getValue(extracted)).isEqualTo("joey");
  }

  @Test public void customSampler() throws IOException {
    String path = "/foo";

    closeClient(client);

    SamplerFunction<HttpRequest> sampler = HttpRuleSampler.newBuilder()
      .putRule(pathStartsWith(path), Sampler.NEVER_SAMPLE)
      .build();

    httpTracing = httpTracing.toBuilder().clientSampler(sampler).build();
    client = newClient(server.getPort());

    server.enqueue(new MockResponse());
    get(client, path);

    assertThat(extract(takeRequest()).sampled()).isFalse();
  }

  /** This prevents confusion as a blocking client should end before, the start of the next span. */
  @Test public void clientTimestampAndDurationEnclosedByParent() throws IOException {
    server.enqueue(new MockResponse());

    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    Clock clock = tracing.clock(parent);

    long start = clock.currentTimeMicroseconds();
    try (Scope scope = currentTraceContext.newScope(parent)) {
      get(client, "/foo");
    }
    long finish = clock.currentTimeMicroseconds();

    Span clientSpan = reporter.takeRemoteSpan(Span.Kind.CLIENT);
    assertChildOf(clientSpan, parent);
    assertSpanInInterval(clientSpan, start, finish);
  }

  @Test public void reportsClientKindToZipkin() throws IOException {
    server.enqueue(new MockResponse());
    get(client, "/foo");

    reporter.takeRemoteSpan(Span.Kind.CLIENT);
  }

  @Test
  public void reportsServerAddress() throws IOException {
    server.enqueue(new MockResponse());
    get(client, "/foo");

    assertThat(reporter.takeRemoteSpan(Span.Kind.CLIENT).remoteEndpoint())
      .isEqualTo(Endpoint.newBuilder()
        .ip("127.0.0.1")
        .port(server.getPort()).build()
      );
  }

  @Test public void defaultSpanNameIsMethodName() throws IOException {
    server.enqueue(new MockResponse());
    get(client, "/foo");

    assertThat(reporter.takeRemoteSpan(Span.Kind.CLIENT).name())
      .isEqualTo("get");
  }

  @Test public void readsRequestAtResponseTime() throws IOException {
    String uri = "/foo/bar?z=2&yAA=1";

    closeClient(client);
    httpTracing = httpTracing.toBuilder()
      .clientResponseParser((response, context, span) -> {
        HttpTags.URL.tag(response.request(), span); // just the path is tagged by default
      })
      .build();

    client = newClient(server.getPort());
    server.enqueue(new MockResponse());
    get(client, uri);

    assertThat(reporter.takeRemoteSpan(Span.Kind.CLIENT).tags())
      .containsEntry("http.url", url(uri));
  }

  @Test public void supportsPortableCustomization() throws IOException {
    String uri = "/foo/bar?z=2&yAA=1";

    closeClient(client);
    httpTracing = httpTracing.toBuilder()
      .clientRequestParser((request, context, span) -> {
        span.name(request.method().toLowerCase() + " " + request.path());
        HttpTags.URL.tag(request, span); // just the path is tagged by default
        span.tag("request_customizer.is_span", (span instanceof brave.Span) + "");
      })
      .clientResponseParser((response, context, span) -> {
        HttpResponseParser.DEFAULT.parse(response, context, span);
        span.tag("response_customizer.is_span", (span instanceof brave.Span) + "");
      })
      .build().clientOf("remote-service");

    client = newClient(server.getPort());
    server.enqueue(new MockResponse());
    get(client, uri);

    Span span = reporter.takeRemoteSpan(Span.Kind.CLIENT);
    assertThat(span.name())
      .isEqualTo("get /foo/bar");

    assertThat(span.remoteServiceName())
      .isEqualTo("remote-service");

    assertThat(span.tags())
      .containsEntry("http.url", url(uri))
      .containsEntry("request_customizer.is_span", "false")
      .containsEntry("response_customizer.is_span", "false");
  }

  @Deprecated @Test public void supportsDeprecatedPortableCustomization() throws IOException {
    String uri = "/foo/bar?z=2&yAA=1";

    closeClient(client);
    httpTracing = httpTracing.toBuilder()
      .clientParser(new HttpClientParser() {
        @Override
        public <Req> void request(HttpAdapter<Req, ?> adapter, Req req,
          SpanCustomizer customizer) {
          customizer.name(adapter.method(req).toLowerCase() + " " + adapter.path(req));
          customizer.tag("http.url", adapter.url(req)); // just the path is tagged by default
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

    Span span = reporter.takeRemoteSpan(Span.Kind.CLIENT);
    assertThat(span.name())
      .isEqualTo("get /foo/bar");

    assertThat(span.remoteServiceName())
      .isEqualTo("remote-service");

    assertThat(span.tags())
      .containsEntry("http.url", url(uri))
      .containsEntry("context.visible", "true")
      .containsEntry("request_customizer.is_span", "false")
      .containsEntry("response_customizer.is_span", "false");
  }

  @Test public void addsStatusCodeWhenNotOk() throws IOException {
    server.enqueue(new MockResponse().setResponseCode(400));

    try {
      get(client, "/foo");
    } catch (RuntimeException e) {
      // some clients raise 400 as an exception such as HttpClientError
    }

    assertThat(reporter.takeRemoteSpanWithError(Span.Kind.CLIENT, "400").tags())
      .containsEntry("http.status_code", "400");
  }

  @Test public void redirect() throws IOException {
    server.enqueue(new MockResponse().setResponseCode(302)
      .addHeader("Location: " + url("/bar")));
    server.enqueue(new MockResponse().setResponseCode(404)); // hehe to a bad location!

    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      get(client, "/foo");
    } catch (RuntimeException e) {
      // some clients raise 404 as an exception such as HttpClientError
    }

    Span initial = reporter.takeRemoteSpan(Span.Kind.CLIENT);
    Span redirected = reporter.takeRemoteSpanWithError(Span.Kind.CLIENT, "404");

    for (Span child : Arrays.asList(initial, redirected)) {
      assertChildOf(child, parent);
    }

    assertSequential(initial, redirected);

    assertThat(initial.tags().get("http.path")).isEqualTo("/foo");
    assertThat(redirected.tags().get("http.path")).isEqualTo("/bar");
  }

  @Test public void post() throws IOException {
    String path = "/post";
    String body = "body";
    server.enqueue(new MockResponse());

    post(client, path, body);

    assertThat(takeRequest().getBody().readUtf8())
      .isEqualTo(body);

    assertThat(reporter.takeRemoteSpan(Span.Kind.CLIENT).name())
      .isEqualTo("post");
  }

  @Test public void httpPathTagExcludesQueryParams() throws IOException {
    String path = "/foo?z=2&yAA=1";

    server.enqueue(new MockResponse());
    get(client, path);

    assertThat(reporter.takeRemoteSpan(Span.Kind.CLIENT).tags())
      .containsEntry("http.path", "/foo");
  }

  @Test public void finishedSpanHandlerSeesException() throws IOException {
    finishedSpanHandlerSeesException(get());
  }

  @Test public void errorTag_onTransportException() {
    checkReportsSpanOnTransportException(get());
  }

  Callable<Void> get() {
    return () -> {
      get(client, "/foo");
      return null;
    };
  }

  /**
   * This ensures custom finished span handlers can see the actual exception thrown, not just the
   * "error" tag value.
   */
  void finishedSpanHandlerSeesException(Callable<Void> get) throws IOException {
    AtomicReference<Throwable> caughtThrowable = new AtomicReference<>();
    closeClient(client);
    httpTracing = HttpTracing.create(tracingBuilder(Sampler.ALWAYS_SAMPLE)
      .addFinishedSpanHandler(new FinishedSpanHandler() {
        @Override public boolean handle(TraceContext context, MutableSpan span) {
          caughtThrowable.set(span.error());
          return true;
        }
      })
      .build());
    client = newClient(server.getPort());

    checkReportsSpanOnTransportException(get);
    assertThat(caughtThrowable.get()).isNotNull();
  }

  Span checkReportsSpanOnTransportException(Callable<Void> get) {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

    try {
      get.call();
    } catch (Exception e) {
      // ok, but the span should include an error!
    }

    // We don't know the transport exception
    return reporter.takeRemoteSpanWithError(Span.Kind.CLIENT, ".+");
  }

  protected String url(String pathIncludingQuery) {
    return "http://127.0.0.1:" + server.getPort() + pathIncludingQuery;
  }

  /** Ensures a timeout receiving a request happens before the method timeout */
  protected RecordedRequest takeRequest() {
    try {
      return server.takeRequest(3, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  protected TraceContext extract(RecordedRequest request) {
    TraceContextOrSamplingFlags extracted = extractor.extract(request);
    assertThat(extracted.context())
      .withFailMessage("Expected to extract a trace context from %s", request.getHeaders())
      .isNotNull();
    return extracted.context();
  }
}
