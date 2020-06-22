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
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.http.HttpAdapter;
import brave.http.HttpClientParser;
import brave.http.HttpRequest;
import brave.http.HttpResponseParser;
import brave.http.HttpRuleSampler;
import brave.http.HttpTags;
import brave.http.HttpTracing;
import brave.propagation.CurrentTraceContext.Scope;
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
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static brave.Span.Kind.CLIENT;
import static brave.http.HttpRequestMatchers.pathStartsWith;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class ITHttpClient<C> extends ITRemote {
  @Rule public MockWebServer server = new MockWebServer();

  protected C client;
  protected HttpTracing httpTracing = HttpTracing.create(tracing);
  protected Extractor<RecordedRequest> extractor =
    propagationFactory.get().extractor(RecordedRequest::getHeader);

  @Before public void setup() throws IOException {
    client = newClient(server.getPort());
  }

  /** Make sure the client you return has retries disabled. */
  protected abstract C newClient(int port) throws IOException;

  protected abstract void closeClient(C client) throws IOException;

  protected abstract void options(C client, String path) throws IOException;

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
    assertSameIds(testSpanHandler.takeRemoteSpan(CLIENT), extracted);
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
    assertSameIds(testSpanHandler.takeRemoteSpan(CLIENT), extracted);
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

    testSpanHandler.takeRemoteSpan(CLIENT);
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

    MutableSpan clientSpan = testSpanHandler.takeRemoteSpan(CLIENT);
    assertChildOf(clientSpan, parent);
    assertSpanInInterval(clientSpan, start, finish);
  }

  @Test public void reportsClientKindToZipkin() throws IOException {
    server.enqueue(new MockResponse());
    get(client, "/foo");

    testSpanHandler.takeRemoteSpan(CLIENT);
  }

  @Test
  public void reportsServerAddress() throws IOException {
    server.enqueue(new MockResponse());
    get(client, "/foo");

    assertThat(testSpanHandler.takeRemoteSpan(CLIENT))
        .extracting(MutableSpan::remoteIp, MutableSpan::remotePort)
        .containsExactly("127.0.0.1", server.getPort());
  }

  @Test public void defaultSpanNameIsMethodName() throws IOException {
    server.enqueue(new MockResponse());
    get(client, "/foo");

    assertThat(testSpanHandler.takeRemoteSpan(CLIENT).name())
      .isEqualTo("GET");
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

    assertThat(testSpanHandler.takeRemoteSpan(CLIENT).tags())
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

    MutableSpan span = testSpanHandler.takeRemoteSpan(CLIENT);
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

    MutableSpan span = testSpanHandler.takeRemoteSpan(CLIENT);
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

    assertThat(testSpanHandler.takeRemoteSpanWithErrorTag(CLIENT, "400").tags())
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

    MutableSpan initial = testSpanHandler.takeRemoteSpan(CLIENT);
    MutableSpan redirected = testSpanHandler.takeRemoteSpanWithErrorTag(CLIENT, "404");

    for (MutableSpan child : Arrays.asList(initial, redirected)) {
      assertChildOf(child, parent);
    }

    assertSequential(initial, redirected);

    assertThat(initial.tags().get("http.path")).isEqualTo("/foo");
    assertThat(redirected.tags().get("http.path")).isEqualTo("/bar");
  }

  /** This tests empty path "" coerces to "/" per RFC 7230 Section 2.7.3 */
  @Test public void emptyPath() throws IOException {
    server.enqueue(new MockResponse());

    get(client, "");

    assertThat(takeRequest().getPath())
      .isEqualTo("/");

    assertThat(testSpanHandler.takeRemoteSpan(CLIENT).tags())
      .containsEntry("http.path", "/");
  }

  @Test public void options() throws IOException {
    server.enqueue(new MockResponse().setResponseCode(204));

    // Not using asterisk-form (RFC 7230 Section 5.3.4) as many clients don't support it
    options(client, "");

    assertThat(takeRequest().getMethod())
      .isEqualTo("OPTIONS");

    assertThat(testSpanHandler.takeRemoteSpan(CLIENT).tags())
      .containsEntry("http.method", "OPTIONS")
      .containsEntry("http.path", "/");
  }

  @Test public void post() throws IOException {
    String path = "/post";
    String body = "body";
    server.enqueue(new MockResponse());

    post(client, path, body);

    assertThat(takeRequest().getBody().readUtf8())
      .isEqualTo(body);

    assertThat(testSpanHandler.takeRemoteSpan(CLIENT).name())
      .isEqualTo("POST");
  }

  @Test public void httpPathTagExcludesQueryParams() throws IOException {
    String path = "/foo?z=2&yAA=1";

    server.enqueue(new MockResponse());
    get(client, path);

    assertThat(testSpanHandler.takeRemoteSpan(CLIENT).tags())
      .containsEntry("http.path", "/foo");
  }

  @Test public void spanHandlerSeesError() throws IOException {
    spanHandlerSeesError(get());
  }

  @Test public void setsError_onTransportException() {
    checkReportsSpanOnTransportException(get());
  }

  Callable<Void> get() {
    return () -> {
      get(client, "/foo");
      return null;
    };
  }

  /**
   * This ensures custom span handlers can see the actual exception thrown, not just the "error"
   * tag value.
   */
  void spanHandlerSeesError(Callable<Void> get) throws IOException {
    ConcurrentLinkedDeque<Throwable> caughtThrowables = new ConcurrentLinkedDeque<>();
    closeClient(client);
    httpTracing = HttpTracing.create(tracingBuilder(Sampler.ALWAYS_SAMPLE)
      .clearSpanHandlers()
      .addSpanHandler(new SpanHandler() {
        @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
          Throwable error = span.error();
          if (error != null) {
            caughtThrowables.add(error);
          } else {
            caughtThrowables.add(new RuntimeException("Unexpected additional call to end"));
          }
          return true;
        }
      })
      // The blocking span handler goes after the error catcher, so we can assert on the errors.
      .addSpanHandler(testSpanHandler)
      .build());
    client = newClient(server.getPort());

    // If this passes, a span was reported with an error
    checkReportsSpanOnTransportException(get);

    assertThat(caughtThrowables)
        .withFailMessage("Span finished with error, but caughtThrowables empty")
        .isNotEmpty();
    if (caughtThrowables.size() > 1) {
      for (Throwable throwable : caughtThrowables) {
        Logger.getAnonymousLogger().log(Level.SEVERE, "multiple calls to finish", throwable);
      }
      assertThat(caughtThrowables).hasSize(1);
    }
  }

  MutableSpan checkReportsSpanOnTransportException(Callable<Void> get) {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

    try {
      get.call();
    } catch (Exception e) {
      // ok, but the span should include an error!
    }

    // We don't know the transport exception
    return testSpanHandler.takeRemoteSpanWithError(CLIENT);
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
