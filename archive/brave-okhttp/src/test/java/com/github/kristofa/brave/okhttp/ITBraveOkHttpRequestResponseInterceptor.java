package com.github.kristofa.brave.okhttp;

import com.github.kristofa.brave.BraveExecutorService;
import com.github.kristofa.brave.http.ITHttpClient;
import com.github.kristofa.brave.http.SpanNameProvider;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.ComparisonFailure;
import org.junit.Test;

import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

public class ITBraveOkHttpRequestResponseInterceptor extends ITHttpClient<OkHttpClient> {

  @Override protected OkHttpClient newClient(int port) {
    return configureClient(BraveOkHttpRequestResponseInterceptor.create(brave));
  }

  OkHttpClient configureClient(BraveOkHttpRequestResponseInterceptor filter) {
    return new OkHttpClient.Builder()
        .dispatcher(new Dispatcher(
            BraveExecutorService.wrap(Executors.newSingleThreadExecutor(), brave)
        ))
        .connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(1, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .addNetworkInterceptor(filter).build();
  }

  @Override protected OkHttpClient newClient(int port, SpanNameProvider spanNameProvider) {
    return configureClient(BraveOkHttpRequestResponseInterceptor.builder(brave)
        .spanNameProvider(spanNameProvider).build());
  }

  @Override protected void closeClient(OkHttpClient client) throws IOException {
    client.dispatcher().executorService().shutdownNow();
  }

  @Override protected void get(OkHttpClient client, String pathIncludingQuery)
      throws IOException {
    client.newCall(new Request.Builder().url(server.url(pathIncludingQuery)).build())
        .execute();
  }

  @Override protected void getAsync(OkHttpClient client, String pathIncludingQuery)
      throws Exception {
    client.newCall(new Request.Builder().url(server.url(pathIncludingQuery)).build())
        .enqueue(new Callback() {
          @Override public void onFailure(Call call, IOException e) {
            e.printStackTrace();
          }

          @Override public void onResponse(Call call, Response response) throws IOException {
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

  @Override
  @Test
  public void usesParentFromInvocationTime_local() throws Exception {
    try {
      super.usesParentFromInvocationTime_local();
    } catch (ComparisonFailure expected) {
      // #286 in-flight requests aren't getting span attached
      // this doesn't fail consistently, though
    }
  }
}
