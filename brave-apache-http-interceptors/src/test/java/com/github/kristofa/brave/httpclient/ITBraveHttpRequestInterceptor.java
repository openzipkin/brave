package com.github.kristofa.brave.httpclient;

import com.github.kristofa.brave.http.ITHttpClient;
import com.github.kristofa.brave.http.SpanNameProvider;
import java.io.IOException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.AssumptionViolatedException;
import org.junit.Test;

public class ITBraveHttpRequestInterceptor extends ITHttpClient<CloseableHttpClient> {

  @Override protected CloseableHttpClient newClient(int port) {
    return configureClient(BraveHttpRequestInterceptor.create(brave));
  }

  CloseableHttpClient configureClient(BraveHttpRequestInterceptor requestInterceptor) {
    return HttpClients.custom()
        .disableAutomaticRetries()
        .addInterceptorFirst(requestInterceptor)
        .addInterceptorFirst(BraveHttpResponseInterceptor.create(brave))
        .build();
  }

  @Override protected CloseableHttpClient newClient(int port, SpanNameProvider spanNameProvider) {
    return configureClient(BraveHttpRequestInterceptor.builder(brave)
        .spanNameProvider(spanNameProvider).build());
  }

  @Override protected void closeClient(CloseableHttpClient client) throws IOException {
    client.close();
  }

  @Override protected void get(CloseableHttpClient client, String pathIncludingQuery)
      throws IOException {
    client.execute(new HttpGet(server.url("/foo").uri())).close();
  }

  @Override protected void getAsync(CloseableHttpClient client, String pathIncludingQuery) {
    throw new AssumptionViolatedException("TODO: HttpAsyncClients");
  }

  @Override
  @Test(expected = AssertionError.class) // doesn't yet close a span on exception
  public void addsErrorTagOnTransportException() throws Exception {
    super.addsErrorTagOnTransportException();
  }

  @Override
  @Test(expected = AssertionError.class) // base url is not logged in apache
  public void httpUrlTagIncludesQueryParams() throws Exception {
    super.httpUrlTagIncludesQueryParams();
  }

  @Override
  @Test(expected = AssertionError.class) // doesn't yet close a span on exception
  public void reportsSpanOnTransportException() throws Exception {
    super.reportsSpanOnTransportException();
  }
}
