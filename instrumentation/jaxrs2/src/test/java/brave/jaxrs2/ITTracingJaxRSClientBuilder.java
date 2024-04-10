/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jaxrs2;

import brave.test.http.ITHttpAsyncClient;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;

import static java.util.concurrent.Executors.newCachedThreadPool;

class ITTracingJaxRSClientBuilder extends ITHttpAsyncClient<Client> {
  ExecutorService executorService = currentTraceContext.executorService(newCachedThreadPool());

  @Override protected Client newClient(int port) {
    return new ResteasyClientBuilder()
        .register(TracingClientFilter.create(httpTracing))
        .connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(1, TimeUnit.SECONDS)
        .executorService(executorService)
        .build();
  }

  @AfterEach @Override public void close() throws Exception {
    executorService.shutdown();
    executorService.awaitTermination(1, TimeUnit.SECONDS);
    super.close();
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
  protected void get(Client client, String path, BiConsumer<Integer, Throwable> callback) {
    client.target(url(path))
        .request(MediaType.TEXT_PLAIN_TYPE)
        .async()
        .get(new InvocationCallback<Response>() {
          @Override public void completed(Response response) {
            callback.accept(response.getStatus(), null);
          }

          @Override public void failed(Throwable throwable) {
            callback.accept(null, throwable);
          }
        });
  }

  @Override protected void options(Client client, String path) throws IOException {
    client.target(url(path))
      .request(MediaType.TEXT_PLAIN_TYPE)
      .options();
  }

  @Override
  protected void post(Client client, String pathIncludingQuery, String body) {
    client.target(url(pathIncludingQuery))
        .request(MediaType.TEXT_PLAIN_TYPE)
        .post(Entity.text(body), String.class);
  }

  @Override @Disabled("automatic error propagation is impossible")
  public void setsError_onTransportException() {
  }

  @Override @Disabled("automatic error propagation is impossible")
  public void spanHandlerSeesError() {
  }

  @Override @Disabled("blind to the implementation of redirects")
  public void redirect() {
  }

  @Override @Disabled("doesn't know the remote address")
  public void reportsServerAddress() {
  }
}
