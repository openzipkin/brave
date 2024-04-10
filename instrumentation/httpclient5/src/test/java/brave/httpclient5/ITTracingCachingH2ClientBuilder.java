/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.httpclient5;

import brave.handler.MutableSpan;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import java.io.IOException;
import java.util.Arrays;
import okhttp3.mockwebserver.MockResponse;
import org.apache.hc.client5.http.impl.async.H2AsyncClientBuilder;
import org.apache.hc.client5.http.impl.cache.CachingH2AsyncClientBuilder;
import org.junit.jupiter.api.Test;

import static brave.Span.Kind.CLIENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ITTracingCachingH2ClientBuilder extends ITTracingH2AsyncClientBuilder {

  @Override
  protected H2AsyncClientBuilder newClientBuilder() {
    return CachingH2AsyncClientBuilder.create().disableAutomaticRetries();
  }

  /**
   * Handle when the client doesn't actually make a client span
   */
  @Test void cacheControl() throws IOException {
    server.enqueue(new MockResponse()
      .addHeader("Content-Type", "text/plain")
      .addHeader("Cache-Control", "max-age=600, stale-while-revalidate=1200")
      .setBody("Hello"));

    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      get(client, "/cached");
      get(client, "/cached");
    }

    assertThat(server.getRequestCount()).isEqualTo(1);

    MutableSpan real = testSpanHandler.takeRemoteSpan(CLIENT);
    MutableSpan cached = testSpanHandler.takeLocalSpan();
    assertThat(cached.tags()).containsKey("http.cache_hit");

    for (MutableSpan child : Arrays.asList(real, cached)) {
      assertChildOf(child, parent);
    }
    assertSequential(real, cached);
  }

  /**
   * Caching Client will throw error asynchronously.
   */
  @Override
  @Test
  public void failedRequestInterceptorRemovesScope() {
    assertThat(currentTraceContext.get()).isNull();
    RuntimeException error = new RuntimeException("Test");
    client = HttpClient5Tracing.newBuilder(httpTracing)
      .build(newClientBuilder()
        .addRequestInterceptorLast((httpRequest, entityDetails, httpContext) -> {
          throw error;
        }));
    client.start();

    assertThatThrownBy(() -> get(client, "/foo"))
      .hasRootCause(error);

    assertThat(currentTraceContext.get()).isNull();

    testSpanHandler.takeRemoteSpanWithError(CLIENT, error);
  }
}
