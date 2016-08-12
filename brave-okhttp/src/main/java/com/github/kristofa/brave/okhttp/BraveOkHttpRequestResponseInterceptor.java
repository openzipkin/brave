package com.github.kristofa.brave.okhttp;


import java.io.IOException;

import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.http.HttpClientRequest;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.HttpClientResponseAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class BraveOkHttpRequestResponseInterceptor implements Interceptor {

  private final ClientRequestInterceptor clientRequestInterceptor;
  private final ClientResponseInterceptor clientResponseInterceptor;
  private final SpanNameProvider spanNameProvider;

  public BraveOkHttpRequestResponseInterceptor(ClientRequestInterceptor requestInterceptor,
                                               ClientResponseInterceptor responseInterceptor,
                                               SpanNameProvider spanNameProvider) {
    this.spanNameProvider = spanNameProvider;
    this.clientRequestInterceptor = requestInterceptor;
    this.clientResponseInterceptor = responseInterceptor;
  }

  @Override
  public Response intercept(Interceptor.Chain chain) throws IOException {
    Request request = chain.request();
    Request.Builder builder = request.newBuilder();
    OkHttpRequest okHttpRequest = new OkHttpRequest(builder, request);
    clientRequestInterceptor.handle(createRequestAdapter(okHttpRequest));
    Response response = chain.proceed(builder.build());
    clientResponseInterceptor.handle(createResponseAdapter(response));
    return response;
  }

  protected SpanNameProvider getSpanNameProvider() {
    return spanNameProvider;
  }

  protected HttpClientRequestAdapter createRequestAdapter(HttpClientRequest request) {
    return new HttpClientRequestAdapter(request, getSpanNameProvider());
  }

  protected HttpClientResponseAdapter createResponseAdapter(Response response) {
    return new HttpClientResponseAdapter(new OkHttpResponse(response));
  }

}
