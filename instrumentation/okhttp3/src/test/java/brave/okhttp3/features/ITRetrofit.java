/*
 * Copyright 2013-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
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
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.http.GET;

import static brave.Span.Kind.CLIENT;
import static org.assertj.core.api.Assertions.assertThat;

/** This tests integration with Retrofit */
public class ITRetrofit extends ITRemote {
  @Rule public MockWebServer server = new MockWebServer();
  HttpTracing httpTracing = HttpTracing.create(tracing);

  // Dispatcher/ExecutorService managed externally only to reduce chance of flakey tests
  Dispatcher dispatcher = new Dispatcher();
  ExecutorService executorService = dispatcher.executorService();

  Service service;

  @Before public void setup()  {
    service = new Retrofit.Builder()
      .baseUrl(server.url("/"))
      .callFactory(TracingCallFactory.create(httpTracing, new OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .build()
      ))
      .build()
      .create(Service.class);
  }

  @After @Override public void close() throws Exception {
    executorService.shutdown();
    executorService.awaitTermination(1, TimeUnit.SECONDS);
    super.close();
  }

  interface Service {
    @GET("/")
    Call<ResponseBody> call();
  }

  @Test public void basicSynchronousCall() throws Exception {
    server.enqueue(new MockResponse());

    service.call().execute();

    assertThat(server.takeRequest().getHeader("x-b3-traceid"))
      .withFailMessage("Trace headers weren't added!")
      .isNotNull();

    assertThat(testSpanHandler.takeRemoteSpan(CLIENT).tags())
      .containsKey("http.path")
      .withFailMessage("HTTP span wasn't reported!")
      .isNotNull();

    // Context propagation, ex using RxJava2 or similar to follow-up with another call,
    // is a different matter. See brave-context-rxjava2
  }
}
