/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
package brave.features.async;

import brave.Span;
import brave.Tracing;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * This is an example of a one-way span, which is possible by use of the {@link Span#flush()}
 * operator.
 */
public class OneWaySpanTest {
  @Rule public MockWebServer server = new MockWebServer();

  BlockingQueue<zipkin2.Span> spans = new LinkedBlockingQueue<>();

  /** Use different tracers for client and server as usually they are on different hosts. */
  Tracing clientTracing = Tracing.newBuilder()
    .localServiceName("client")
    .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
      .addScopeDecorator(StrictScopeDecorator.create())
      .build())
    .spanReporter(spans::add)
    .build();
  Tracing serverTracing = Tracing.newBuilder()
    .localServiceName("server")
    .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
      .addScopeDecorator(StrictScopeDecorator.create())
      .build())
    .spanReporter(spans::add)
    .build();

  CountDownLatch flushedIncomingRequest = new CountDownLatch(1);

  @Before public void setup() {
    server.setDispatcher(new Dispatcher() {
      @Override public MockResponse dispatch(RecordedRequest recordedRequest) {
        // pull the context out of the incoming request
        TraceContextOrSamplingFlags extracted = serverTracing.propagation()
          .extractor(RecordedRequest::getHeader).extract(recordedRequest);

        Span span = extracted.context() != null
          ? serverTracing.tracer().joinSpan(extracted.context())
          : serverTracing.tracer().nextSpan(extracted);

        span.name(recordedRequest.getMethod())
          .kind(Span.Kind.SERVER)
          .start().flush(); // start the server side and flush instead of processing a response

        flushedIncomingRequest.countDown();
        // eventhough the client doesn't read the response, we return one
        return new MockResponse();
      }
    });
  }

  @After public void close() {
    clientTracing.close();
    serverTracing.close();
  }

  @Test(timeout = 1000L)
  public void startWithOneTracerAndStopWithAnother() throws Exception {
    // start a new span representing a request
    Span span = clientTracing.tracer().newTrace();

    // inject the trace context into the request
    Request.Builder request = new Request.Builder().url(server.url("/"));
    clientTracing.propagation()
      .injector(Request.Builder::addHeader).inject(span.context(), request);

    // fire off the request asynchronously, totally dropping any response
    new OkHttpClient().newCall(request.build()).enqueue(mock(Callback.class));
    // start the client side and flush instead of processing a response
    span.kind(Span.Kind.CLIENT).start().flush();

    // check that the client send arrived first
    zipkin2.Span clientSpan = spans.take();
    assertThat(clientSpan.name()).isNull();
    assertThat(clientSpan.localServiceName())
      .isEqualTo("client");
    assertThat(clientSpan.kind())
      .isEqualTo(zipkin2.Span.Kind.CLIENT);

    // check that the server receive arrived last
    zipkin2.Span serverSpan = spans.take();
    assertThat(serverSpan.name()).isEqualTo("get");
    assertThat(serverSpan.localServiceName())
      .isEqualTo("server");
    assertThat(serverSpan.kind())
      .isEqualTo(zipkin2.Span.Kind.SERVER);

    // check that the server span is shared
    assertThat(serverSpan.shared()).isTrue();

    // check that no spans reported duration
    assertThat(clientSpan.duration()).isNull();
    assertThat(serverSpan.duration()).isNull();
  }
}
