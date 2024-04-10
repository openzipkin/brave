/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.okhttp3.features;

import brave.http.HttpTracing;
import brave.okhttp3.TracingCallFactory;
import brave.test.ITRemote;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.http.GET;

import static brave.Span.Kind.CLIENT;
import static org.assertj.core.api.Assertions.assertThat;

/** This tests integration with Retrofit */
class ITRetrofit extends ITRemote {
  MockWebServer server = new MockWebServer();
  HttpTracing httpTracing = HttpTracing.create(tracing);

  // Dispatcher/ExecutorService managed externally only to reduce chance of flakey tests
  Dispatcher dispatcher = new Dispatcher();
  ExecutorService executorService = dispatcher.executorService();

  Service service;

  @BeforeEach void setup() {
    service = new Retrofit.Builder()
      .baseUrl(server.url("/"))
      .callFactory(TracingCallFactory.create(httpTracing, new OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .build()
      ))
      .build()
      .create(Service.class);
  }

  @AfterEach
  @Override
  public void close() throws Exception {
    executorService.shutdown();
    executorService.awaitTermination(1, TimeUnit.SECONDS);
    super.close();
    server.close();
  }

  interface Service {
    @GET("/")
    Call<ResponseBody> call();
  }

  @Test void basicSynchronousCall() throws Exception {
    server.enqueue(new MockResponse());

    service.call().execute();

    assertThat(server.takeRequest().getHeader("x-b3-traceid"))
      .withFailMessage("Trace headers weren't added!")
      .isNotNull();

    assertThat(testSpanHandler.takeRemoteSpan(CLIENT).tags())
      .containsKey("http.path")
      .withFailMessage("HTTP span wasn't reported!")
      .isNotNull();
  }
}
