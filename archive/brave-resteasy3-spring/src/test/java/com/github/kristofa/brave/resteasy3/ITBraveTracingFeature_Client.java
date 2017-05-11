package com.github.kristofa.brave.resteasy3;

import com.github.kristofa.brave.BraveExecutorService;
import com.github.kristofa.brave.http.ITHttpClient;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.github.kristofa.brave.jaxrs2.BraveTracingFeature;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.client.InvocationCallback;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.junit.Test;

public class ITBraveTracingFeature_Client extends ITHttpClient<ResteasyClient> {

  ExecutorService executor = Executors.newSingleThreadExecutor();

  @Override protected ResteasyClient newClient(int port) {
    return configureClient(BraveTracingFeature.create(brave));
  }

  ResteasyClient configureClient(BraveTracingFeature feature) {
    return new ResteasyClientBuilder()
        .socketTimeout(1, TimeUnit.SECONDS)
        .establishConnectionTimeout(1, TimeUnit.SECONDS)
        .asyncExecutor(BraveExecutorService.wrap(executor, brave))
        .register(feature)
        .build();
  }

  @Override protected ResteasyClient newClient(int port, SpanNameProvider spanNameProvider) {
    return configureClient(BraveTracingFeature.builder(brave)
        .spanNameProvider(spanNameProvider).build());
  }

  @Override protected void closeClient(ResteasyClient client) throws IOException {
    if (client != null) client.close();
    executor.shutdownNow();
  }

  @Override protected void get(ResteasyClient client, String pathIncludingQuery)
      throws IOException {
    client.target(server.url(pathIncludingQuery).uri()).request().buildGet().invoke().close();
  }

  @Override protected void getAsync(ResteasyClient client, String pathIncludingQuery) {
    client.target(server.url(pathIncludingQuery).uri()).request().async().get(
        new InvocationCallback<Void>() {
          @Override public void completed(Void o) {
          }

          @Override public void failed(Throwable throwable) {
            throwable.printStackTrace();
          }
        });
  }

  @Override
  @Test(expected = AssertionError.class) // doesn't yet close a span on exception
  public void reportsSpanOnTransportException() throws Exception {
    super.reportsSpanOnTransportException();
  }

  @Override
  @Test(expected = AssertionError.class) // doesn't yet close a span on exception
  public void addsErrorTagOnTransportException() throws Exception {
    super.addsErrorTagOnTransportException();
  }
}
