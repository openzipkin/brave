package com.github.kristofa.brave.jersey;

import com.github.kristofa.brave.BraveExecutorService;
import com.github.kristofa.brave.http.ITHttpClient;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.sun.jersey.api.client.Client;
import java.io.IOException;
import java.util.concurrent.Executors;
import org.junit.Test;

public class ITJerseyClientTraceFilter extends ITHttpClient<Client> {

  @Override protected Client newClient(int port) {
    return configureClient(JerseyClientTraceFilter.create(brave));
  }

  Client configureClient(JerseyClientTraceFilter filter) {
    Client c = Client.create();
    c.setExecutorService(BraveExecutorService.wrap(Executors.newSingleThreadExecutor(), brave));
    c.setReadTimeout(1000);
    c.setConnectTimeout(1000);
    c.addFilter(filter);
    return c;
  }

  @Override protected Client newClient(int port, SpanNameProvider spanNameProvider) {
    return configureClient(JerseyClientTraceFilter.builder(brave)
        .spanNameProvider(spanNameProvider).build());
  }

  @Override protected void closeClient(Client client) throws IOException {
    client.destroy();
    client.getExecutorService().shutdownNow();
  }

  @Override protected void get(Client client, String pathIncludingQuery)
      throws IOException {
    client.resource(server.url("/foo").uri()).get(String.class);
  }

  @Override protected void getAsync(Client client, String pathIncludingQuery) throws Exception {
    client.asyncResource(server.url("/foo").uri()).get(String.class);
  }


  @Override
  @Test(expected = AssertionError.class) // query params are not logged in jersey
  public void httpUrlTagIncludesQueryParams() throws Exception {
    super.httpUrlTagIncludesQueryParams();
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
