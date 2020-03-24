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
package brave.jaxrs2;

import brave.test.http.ITHttpAsyncClient;
import brave.test.util.AssertableCallback;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.junit.Ignore;

public class ITTracingJaxRSClientBuilder extends ITHttpAsyncClient<Client> {
  @Override protected Client newClient(int port) {
    return new ResteasyClientBuilder()
      .register(TracingClientFilter.create(httpTracing))
      .connectTimeout(1, TimeUnit.SECONDS)
      .readTimeout(1, TimeUnit.SECONDS)
      .executorService(currentTraceContext.executorService(Executors.newCachedThreadPool()))
      .build();
  }

  @Override protected void closeClient(Client client) {
    client.close();
  }

  @Override protected void get(Client client, String pathIncludingQuery) {
    client.target(url(pathIncludingQuery))
      .request(MediaType.TEXT_PLAIN_TYPE)
      .get(String.class);
  }

  @Override
  protected void getAsync(Client client, String path, AssertableCallback<Integer> callback) {
    client.target(url(path))
      .request(MediaType.TEXT_PLAIN_TYPE)
      .async()
      .get(new InvocationCallback<Response>() {
        @Override public void completed(Response response) {
          callback.onSuccess(response.getStatus());
        }

        @Override public void failed(Throwable throwable) {
          callback.onError(throwable);
        }
      });
  }

  @Override
  protected void post(Client client, String pathIncludingQuery, String body) {
    client.target(url(pathIncludingQuery))
      .request(MediaType.TEXT_PLAIN_TYPE)
      .post(Entity.text(body), String.class);
  }

  @Override @Ignore("automatic error propagation is impossible")
  public void errorTag_onTransportException() {
  }

  @Override @Ignore("automatic error propagation is impossible")
  public void finishedSpanHandlerSeesException() {
  }

  @Override @Ignore("blind to the implementation of redirects")
  public void redirect() {
  }

  @Override @Ignore("doesn't know the remote address")
  public void reportsServerAddress() {
  }
}
