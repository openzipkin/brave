package com.github.kristofa.brave.okhttp;


import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.HttpClientResponseAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class BraveOkHttpRequestResponseInterceptor implements Interceptor {

  private final ClientRequestInterceptor clientRequestInterceptor;
  private final ClientResponseInterceptor clientResponseInterceptor;
  private final SpanNameProvider spanNameProvider;

  public BraveOkHttpRequestResponseInterceptor(ClientRequestInterceptor requestInterceptor, ClientResponseInterceptor responseInterceptor, SpanNameProvider spanNameProvider) {
    this.spanNameProvider = spanNameProvider;
    this.clientRequestInterceptor = requestInterceptor;
    this.clientResponseInterceptor = responseInterceptor;
  }

  @Override
  public Response intercept(Interceptor.Chain chain) throws IOException {
    Request request = chain.request();
    Request.Builder builder = request.newBuilder();
    OkHttpRequest okHttpRequest = new OkHttpRequest(builder, request);
    clientRequestInterceptor.handle(new HttpClientRequestAdapter(okHttpRequest, spanNameProvider));
    Response response = chain.proceed(builder.build());
    clientResponseInterceptor.handle(new HttpClientResponseAdapter(new OkHttpResponse(response)));
    return response;
  }

}
