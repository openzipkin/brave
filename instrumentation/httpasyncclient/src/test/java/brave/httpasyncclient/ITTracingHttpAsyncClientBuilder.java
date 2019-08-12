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
package brave.httpasyncclient;

import brave.test.http.ITHttpAsyncClient;
import java.io.IOException;
import java.net.URI;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ITTracingHttpAsyncClientBuilder extends ITHttpAsyncClient<CloseableHttpAsyncClient> {

  @Override protected CloseableHttpAsyncClient newClient(int port) {
    CloseableHttpAsyncClient result = TracingHttpAsyncClientBuilder.create(httpTracing).build();
    result.start();
    return result;
  }

  @Override protected void closeClient(CloseableHttpAsyncClient client) throws IOException {
    client.close();
  }

  @Override protected void get(CloseableHttpAsyncClient client, String pathIncludingQuery)
    throws Exception {
    HttpGet get = new HttpGet(URI.create(url(pathIncludingQuery)));
    EntityUtils.consume(client.execute(get, null).get().getEntity());
  }

  @Override
  protected void post(CloseableHttpAsyncClient client, String pathIncludingQuery, String body)
    throws Exception {
    HttpPost post = new HttpPost(URI.create(url(pathIncludingQuery)));
    post.setEntity(new NStringEntity(body));
    EntityUtils.consume(client.execute(post, null).get().getEntity());
  }

  @Override protected void getAsync(CloseableHttpAsyncClient client, String pathIncludingQuery) {
    client.execute(new HttpGet(URI.create(url(pathIncludingQuery))), null);
  }

  @Test public void currentSpanVisibleToUserFilters() throws Exception {
    server.enqueue(new MockResponse());
    closeClient(client);

    client = TracingHttpAsyncClientBuilder.create(httpTracing)
      .addInterceptorLast((HttpRequestInterceptor) (request, context) ->
        request.setHeader("my-id", currentTraceContext.get().traceIdString())
      ).build();
    client.start();

    get(client, "/foo");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeader("x-b3-traceId"))
      .isEqualTo(request.getHeader("my-id"));

    takeSpan();
  }
}
