/*
 * Copyright 2013-2019 The OpenZipkin Authors
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

import brave.SpanCustomizer;
import brave.http.HttpAdapter;
import brave.http.HttpRuleSampler;
import brave.http.HttpServerParser;
import brave.http.HttpTracing;
import brave.propagation.ExtraFieldPropagation;
import brave.sampler.Sampler;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
   * The /extra endpoint should copy the key {@link #EXTRA_KEY} to the response body using {@link
   * ExtraFieldPropagation#get(String)}.
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
    Response response = get("/async");
    assertThat(response.isSuccessful()).withFailMessage("not successful: " + response).isTrue();

    takeSpan();
  }

  /**
   * This ensures thread-state is propagated from trace interceptors to user code. The endpoint
   * "/child" is expected to create an in-process span. When this works, it should be a child of the
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
      .isEqualTo("1.2.3.4");
  }

  @Test
  public void reportsServerKindToZipkin() throws Exception {
    get("/foo");

    Span span = takeSpan();
    assertThat(span.kind())
      .isEqualTo(Span.Kind.SERVER);
  }

  @Test
  public void defaultSpanNameIsMethodNameOrRoute() throws Exception {
    get("/foo");

    Span span = takeSpan();
    if (!span.name().equals("get")) {
      assertThat(span.name())
        .isEqualTo("get /foo");
    }
  }

  @Test
  public void supportsPortableCustomization() throws Exception {
    httpTracing = httpTracing.toBuilder().serverParser(new HttpServerParser() {
      @Override
      public <Req> void request(HttpAdapter<Req, ?> adapter, Req req, SpanCustomizer customizer) {
        customizer.tag("http.url", adapter.url(req)); // just the path is logged by default
        customizer.tag("context.visible", String.valueOf(currentTraceContext.get() != null));
        customizer.tag("request_customizer.is_span", (customizer instanceof brave.Span) + "");
      }

      @Override
      public <Resp> void response(HttpAdapter<?, Resp> adapter, Resp res, Throwable error,
        SpanCustomizer customizer) {
        super.response(adapter, res, error, customizer);
        customizer.tag("response_customizer.is_span", (customizer instanceof brave.Span) + "");
      }
    }).build();
    init();

    String uri = "/foo?z=2&yAA=1";
    get(uri);

    Span span = takeSpan();
    assertThat(span.tags())
      .containsEntry("http.url", url(uri))
      .containsEntry("context.visible", "true")
      .containsEntry("request_customizer.is_span", "false")
      .containsEntry("response_customizer.is_span", "false");
  }

  /**
   * The "/items/{itemId}" endpoint should return the itemId in the response body, which proves
   * templating worked (including that it ignores query parameters). Note the route format is
   * framework specific, ex "/items/:itemId" in vert.x
   */
  @Test
  public void httpRoute() throws Exception {
    httpTracing = httpTracing.toBuilder().serverParser(addHttpUrlTag).build();
    init();

    routeBasedRequestNameIncludesPathPrefix("/items");
  }

  /**
   * The "/nested/items/{itemId}" endpoint should be implemented by two route expressions: A path
   * prefix: "/nested" and then a relative expression "/items/{itemId}"
   */
  @Test
  public void httpRoute_nested() throws Exception {
    httpTracing = httpTracing.toBuilder().serverParser(addHttpUrlTag).build();
    init();

    routeBasedRequestNameIncludesPathPrefix("/nested/items");
  }

  /**
   * Sometimes state used to carry http route data is different for async requests. This helps
   * ensure we don't miss issues like this.
   */
  @Test
  public void httpRoute_async() throws Exception {
    httpTracing = httpTracing.toBuilder().serverParser(addHttpUrlTag).build();
    init();

    routeBasedRequestNameIncludesPathPrefix("/async_items");
  }

  private void routeBasedRequestNameIncludesPathPrefix(String prefix) throws Exception {
    Response request1 = get(prefix + "/1?foo");
    Response request2 = get(prefix + "/2?bar");

    // get() doesn't check the response, check to make sure the server didn't 500
    assertThat(request1.isSuccessful()).isTrue();
    assertThat(request2.isSuccessful()).isTrue();

    // Reading the route parameter from the response ensures the test endpoint is correct
    assertThat(request1.body().string())
      .isEqualTo("1");
    assertThat(request2.body().string())
      .isEqualTo("2");

    Span span1 = takeSpan(), span2 = takeSpan();

    // verify that the path and url reflect the initial request (not a route expression)
    assertThat(span1.tags())
      .containsEntry("http.method", "GET")
      .containsEntry("http.path", prefix + "/1")
      .containsEntry("http.url", url(prefix + "/1?foo"));
    assertThat(span2.tags())
      .containsEntry("http.method", "GET")
      .containsEntry("http.path", prefix + "/2")
      .containsEntry("http.url", url(prefix + "/2?bar"));

    // We don't know the exact format of the http route as it is framework specific
    // However, we know that it should match both requests and include the common part of the path
    Set<String> routeBasedNames = new LinkedHashSet<>(Arrays.asList(span1.name(), span2.name()));
    assertThat(routeBasedNames).hasSize(1);
    assertThat(routeBasedNames.iterator().next())
      .startsWith("get " + prefix)
      .doesNotEndWith("/") // no trailing slashes
      .doesNotContain("//"); // no duplicate slashes
  }

  final HttpServerParser addHttpUrlTag = new HttpServerParser() {
    @Override
    public <Req> void request(HttpAdapter<Req, ?> adapter, Req req, SpanCustomizer customizer) {
      super.request(adapter, req, customizer);
      customizer.tag("http.url", adapter.url(req)); // just the path is logged by default
    }
  };

  /** If http route is supported, then the span name should include it */
  @Test public void notFound() throws Exception {
    assertThat(call("GET", "/foo/bark").code())
      .isEqualTo(404);

    Span span = takeSpan();

    // verify normal tags
    assertThat(span.tags())
      .hasSize(4)
      .containsEntry("http.method", "GET")
      .containsEntry("http.path", "/foo/bark")
      .containsEntry("http.status_code", "404")
      .containsKey("error"); // as 404 is an error

    // Either the span name is the method, or it is a route expression
    String name = span.name();
    if (name != null && !"get".equals(name)) {
      assertThat(name).isEqualTo("get not_found");
    }
  }

  /**
   * This tests both that a root path ends up as "/" (slash) not "" (empty), as well that less
   * typical OPTIONS methods can be traced.
   */
  @Test public void options() throws Exception {
    assertThat(call("OPTIONS", "/").isSuccessful())
      .isTrue();

    Span span = takeSpan();

    // verify normal tags
    assertThat(span.tags())
      .containsEntry("http.method", "OPTIONS")
      .containsEntry("http.path", "/");

    // Either the span name is the method, or it is a route expression
    String name = span.name();
    if (name != null && !"options".equals(name)) {
      assertThat(name).isEqualTo("options /");
    }
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

  protected Response get(String path) throws Exception {
    return get(new Request.Builder().url(url(path)).build());
  }

  protected Response get(Request request) throws Exception {
    Response response = call(request);
    if (response.code() == 404) {
      // TODO: jetty isn't registering the tracing filter for all paths!
      spans.poll(100, TimeUnit.MILLISECONDS);
      throw new AssumptionViolatedException(request.url().encodedPath() + " not supported");
    }
    return response;
  }

  /** like {@link #get(String)} except doesn't throw unsupported on not found */
  Response call(String method, String path) throws IOException {
    return call(new Request.Builder().method(method, null).url(url(path)).build());
  }

  /** like {@link #get(Request)} except doesn't throw unsupported on not found */
  Response call(Request request) throws IOException {
    try (Response response = client.newCall(request).execute()) {
      if (!HttpHeaders.promisesBody(response)) return response;

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
