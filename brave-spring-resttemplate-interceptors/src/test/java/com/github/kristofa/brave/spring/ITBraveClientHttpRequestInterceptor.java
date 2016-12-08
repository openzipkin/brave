package com.github.kristofa.brave.spring;

import com.github.kristofa.brave.http.ITHttpClient;
import com.github.kristofa.brave.http.SpanNameProvider;
import java.io.IOException;
import java.util.Collections;
import org.junit.AssumptionViolatedException;
import org.junit.Test;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

public class ITBraveClientHttpRequestInterceptor
    extends ITHttpClient<OkHttp3ClientHttpRequestFactory> {

  BraveClientHttpRequestInterceptor interceptor;

  @Override protected OkHttp3ClientHttpRequestFactory newClient(int port) {
    return configureClient(BraveClientHttpRequestInterceptor.create(brave));
  }

  OkHttp3ClientHttpRequestFactory configureClient(BraveClientHttpRequestInterceptor interceptor) {
    OkHttp3ClientHttpRequestFactory factory = new OkHttp3ClientHttpRequestFactory();
    factory.setReadTimeout(1000);
    factory.setConnectTimeout(1000);
    this.interceptor = interceptor;
    return factory;
  }

  @Override
  protected OkHttp3ClientHttpRequestFactory newClient(int port, SpanNameProvider spanNameProvider) {
    return configureClient(BraveClientHttpRequestInterceptor.builder(brave)
        .spanNameProvider(spanNameProvider).build());
  }

  @Override protected void closeClient(OkHttp3ClientHttpRequestFactory client) throws IOException {
    client.destroy();
  }

  @Override protected void get(OkHttp3ClientHttpRequestFactory client, String pathIncludingQuery)
      throws Exception {
    RestTemplate restTemplate = new RestTemplate(client);
    restTemplate.setInterceptors(Collections.singletonList(interceptor));
    restTemplate.getForObject(server.url(pathIncludingQuery).toString(), String.class);
  }

  @Override
  protected void getAsync(OkHttp3ClientHttpRequestFactory client, String pathIncludingQuery) {
    throw new AssumptionViolatedException("TODO: async rest template has its own interceptor");
  }

  @Override
  @Test(expected = AssertionError.class) // doesn't yet add error tag on exception
  public void addsErrorTagOnTransportException() throws Exception {
    super.addsErrorTagOnTransportException();
  }
}
