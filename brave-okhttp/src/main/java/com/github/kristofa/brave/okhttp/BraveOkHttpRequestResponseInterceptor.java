package com.github.kristofa.brave.okhttp;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.HttpClientResponseAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.github.kristofa.brave.internal.Nullable;
import com.github.kristofa.brave.Propagation;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

public class BraveOkHttpRequestResponseInterceptor implements Interceptor {

  /** Creates a tracing interceptor with defaults. Use {@link #builder(Brave)} to customize. */
  public static BraveOkHttpRequestResponseInterceptor create(Brave brave) {
    return new Builder(brave).build();
  }

  public static Builder builder(Brave brave) {
    return new Builder(brave);
  }

  public static final class Builder {
    final Brave brave;
    SpanNameProvider spanNameProvider = new DefaultSpanNameProvider();

    Builder(Brave brave) { // intentionally hidden
      this.brave = checkNotNull(brave, "brave");
    }

    public Builder spanNameProvider(SpanNameProvider spanNameProvider) {
      this.spanNameProvider = checkNotNull(spanNameProvider, "spanNameProvider");
      return this;
    }

    public BraveOkHttpRequestResponseInterceptor build() {
      return new BraveOkHttpRequestResponseInterceptor(this);
    }
  }

  @Nullable // nullable while deprecated constructor is in use
  private final Propagation.Injector<Request.Builder> injector;
  private final ClientRequestInterceptor requestInterceptor;
  private final ClientResponseInterceptor responseInterceptor;
  private final SpanNameProvider spanNameProvider;

  BraveOkHttpRequestResponseInterceptor(Builder b) { // intentionally hidden
    this.injector = b.brave.propagation().injector(Request.Builder::header);
    this.requestInterceptor = b.brave.clientRequestInterceptor();
    this.responseInterceptor = b.brave.clientResponseInterceptor();
    this.spanNameProvider = b.spanNameProvider;
  }

  /**
   * @deprecated please use {@link #create(Brave)} or {@link #builder(Brave)}
   */
  @Deprecated
  public BraveOkHttpRequestResponseInterceptor(ClientRequestInterceptor requestInterceptor, ClientResponseInterceptor responseInterceptor, SpanNameProvider spanNameProvider) {
    this.injector = null;
    this.spanNameProvider = spanNameProvider;
    this.requestInterceptor = requestInterceptor;
    this.responseInterceptor = responseInterceptor;
  }

  @Override
  public Response intercept(Interceptor.Chain chain) throws IOException {
    Request request = chain.request();
    Request.Builder builder = request.newBuilder();
    HttpClientRequestAdapter requestAdapter =
        new HttpClientRequestAdapter(new OkHttpRequest(builder, request), spanNameProvider);
    SpanId spanId = requestInterceptor.internalStartSpan(requestAdapter);
    if (injector != null) {
      injector.injectSpanId(spanId, builder);
    } else {
      requestAdapter.addSpanIdToRequest(spanId);
    }

    Response response = chain.proceed(builder.build());
    responseInterceptor.handle(new HttpClientResponseAdapter(new OkHttpResponse(response)));
    return response;
  }

}
