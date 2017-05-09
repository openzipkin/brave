package com.github.kristofa.brave.jersey2;

import com.github.kristofa.brave.http.ITHttpClient;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.github.kristofa.brave.jaxrs2.BraveTracingFeature;
import java.io.IOException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.Test;

public class ITBraveTracingFeature_Client extends ITHttpClient<Client> {

  @Override protected Client newClient(int port) {
    return configureClient(BraveTracingFeature.create(brave));
  }

  Client configureClient(BraveTracingFeature feature) {
    ClientConfig clientConfig = new ClientConfig();
    clientConfig.register(feature);
    clientConfig.property(ClientProperties.CONNECT_TIMEOUT, 1000);
    clientConfig.property(ClientProperties.READ_TIMEOUT,    1000);
    return ClientBuilder.newClient(clientConfig);
  }

  @Override protected Client newClient(int port, SpanNameProvider spanNameProvider) {
    return configureClient(BraveTracingFeature.builder(brave)
        .spanNameProvider(spanNameProvider).build());
  }

  @Override protected void closeClient(Client client) throws IOException {
    client.close();
  }

  @Override protected void get(Client client, String pathIncludingQuery) throws IOException {
    client.target(server.url("/foo").uri()).request().buildGet().invoke().close();
  }

  @Override protected void getAsync(Client client, String pathIncludingQuery) throws Exception {
    client.target(server.url("/foo").uri()).request().async().get();
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

  @Override
  @Test(expected = AssertionError.class) // #289 attach local span to jersey threadpool
  public void usesParentFromInvocationTime_local() throws Exception {
    super.usesParentFromInvocationTime_local();
  }
}
