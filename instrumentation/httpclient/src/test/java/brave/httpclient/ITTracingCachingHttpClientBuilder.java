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
package brave.httpclient;

import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import java.util.Arrays;
import okhttp3.mockwebserver.MockResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public class ITTracingCachingHttpClientBuilder extends ITTracingHttpClientBuilder {
  @Override protected CloseableHttpClient newClient(int port) {
    return TracingCachingHttpClientBuilder.create(httpTracing).disableAutomaticRetries().build();
  }

  /**
   * Handle when the client doesn't actually make a client span
   *
   * <p>See https://github.com/openzipkin/brave/issues/864
   */
  @Test public void cacheControl() throws Exception {
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

    Span real = takeRemoteSpan(Span.Kind.CLIENT);
    Span cached = takeLocalSpan();
    assertThat(cached.tags()).containsKey("http.cache_hit");

    for (Span child : Arrays.asList(real, cached)) {
      assertChildOf(child, parent);
    }

    assertSequential(real, cached);
  }
}
