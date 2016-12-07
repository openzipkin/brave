package com.github.kristofa.brave.resteasy;

import com.github.kristofa.brave.http.ITHttpClient;
import com.github.kristofa.brave.http.SpanNameProvider;
import java.io.IOException;
import javax.ws.rs.core.UriBuilder;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jboss.resteasy.client.ClientRequestFactory;
import org.jboss.resteasy.client.core.executors.ApacheHttpClient4Executor;
import org.junit.AssumptionViolatedException;
import org.junit.Test;

public class ITBraveClientExecutionInterceptor extends ITHttpClient<ClientRequestFactory> {

  @Override protected ClientRequestFactory newClient(int port) {
    return configureClient(port, BraveClientExecutionInterceptor.create(brave));
  }

  ClientRequestFactory configureClient(int port, BraveClientExecutionInterceptor interceptor) {
    HttpClient httpClient = HttpClients.custom().disableAutomaticRetries().build();

    ApacheHttpClient4Executor clientExecutor = new ApacheHttpClient4Executor(httpClient);
    ClientRequestFactory crf = new ClientRequestFactory(clientExecutor,
        UriBuilder.fromUri("http://localhost:" + port).build());

    crf.getPrefixInterceptors().getExecutionInterceptorList().add(interceptor);
    return crf;
  }

  @Override protected ClientRequestFactory newClient(int port, SpanNameProvider spanNameProvider) {
    return configureClient(port, BraveClientExecutionInterceptor.builder(brave)
        .spanNameProvider(spanNameProvider).build());
  }

  @Override protected void closeClient(ClientRequestFactory client) throws IOException {
    // noop
  }

  @Override protected void get(ClientRequestFactory client, String pathIncludingQuery)
      throws Exception {
    client.createRelativeRequest(pathIncludingQuery).get().releaseConnection();
  }

  @Override protected void getAsync(ClientRequestFactory client, String pathIncludingQuery) {
    throw new AssumptionViolatedException("TODO: how does resteasy 1.x do async?");
  }

  @Override
  @Test(expected = AssertionError.class) // doesn't yet add error tag on exception
  public void addsErrorTagOnTransportException() throws Exception {
    super.addsErrorTagOnTransportException();
  }
}
