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
import org.apache.hc.client5.http.impl.cache.CachingHttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.Test;

import static brave.Span.Kind.CLIENT;
import static org.assertj.core.api.Assertions.assertThat;

class ITTracingCachingHttpClientBuilder extends ITTracingHttpClientBuilder {
  @Override
  protected CloseableHttpClient newClient(int port) {
    return HttpClient5Tracing.newBuilder(httpTracing)
      .build(CachingHttpClientBuilder.create().disableAutomaticRetries());
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
}
