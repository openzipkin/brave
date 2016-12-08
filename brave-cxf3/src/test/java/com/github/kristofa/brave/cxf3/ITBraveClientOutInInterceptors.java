package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.http.ITHttpClient;
import com.github.kristofa.brave.http.SpanNameProvider;
import java.io.IOException;
import okhttp3.HttpUrl;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.junit.AssumptionViolatedException;
import org.junit.Test;

public class ITBraveClientOutInInterceptors extends ITHttpClient<WebClient> {

  @Override protected WebClient newClient(int port) {
    return configureClient(port, BraveClientOutInterceptor.create(brave));
  }

  WebClient configureClient(int port, BraveClientOutInterceptor requestInterceptor) {
    JAXRSClientFactoryBean clientFactory = new JAXRSClientFactoryBean();
    clientFactory.setAddress("http://localhost:" + port);
    clientFactory.getInInterceptors().add(BraveClientInInterceptor.create(brave));
    clientFactory.getOutInterceptors().add(requestInterceptor);
    WebClient client = clientFactory.createWebClient();
    HTTPClientPolicy clientPolicy =
        ((HTTPConduit) WebClient.getConfig(client).getConduit()).getClient();
    clientPolicy.setConnectionTimeout(1000);
    clientPolicy.setReceiveTimeout(1000);
    return client;
  }

  @Override
  protected WebClient newClient(int port, SpanNameProvider spanNameProvider) {
    return configureClient(port, BraveClientOutInterceptor.builder(brave)
        .spanNameProvider(spanNameProvider).build());
  }

  @Override protected void closeClient(WebClient client) throws IOException {
    client.close();
  }

  @Override protected void get(WebClient client, String pathIncludingQuery) throws IOException {
    target(client, pathIncludingQuery).get().close();
  }

  private WebClient target(WebClient client, String pathIncludingQuery) {
    HttpUrl url = server.url(pathIncludingQuery);
    WebClient builder = client.path(url.encodedPath());
    for (String name : url.queryParameterNames()) {
      builder = builder.query(name, url.queryParameterValues(name).toArray());
    }
    return builder;
  }

  @Override protected void getAsync(WebClient client, String pathIncludingQuery) throws Exception {
    target(client, pathIncludingQuery).async().get();
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
