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
package brave.test.http;

import brave.ScopedSpan;
import brave.Tracer;
import brave.propagation.TraceContext;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Test;
import zipkin2.Callback;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class ITHttpAsyncClient<C> extends ITHttpClient<C> {
  static final Callback<Void> NOOP_CALLBACK = new Callback<Void>() {
    @Override public void onSuccess(Void aVoid) {
    }

    @Override public void onError(Throwable throwable) {
    }
  };

  /**
   * This invokes a GET with the indicated path, but does not block until the response is complete.
   * The callback should always be invoked. For example, if there is a cancelation without error,
   * you should invoke the {@link Callback#onError(Throwable)} with your own {@link
   * CancellationException}.
   */
  protected abstract void getAsync(C client, String path, Callback<Void> callback) throws Exception;

  /**
   * This tests that the parent is determined at the time the request was made, not when the request
   * was executed.
   */
  @Test public void usesParentFromInvocationTime() throws Exception {
    Tracer tracer = httpTracing.tracing().tracer();
    server.enqueue(new MockResponse().setBodyDelay(300, TimeUnit.MILLISECONDS));
    server.enqueue(new MockResponse());

    ScopedSpan parent = tracer.startScopedSpan("test");
    try {
      getAsync(client, "/items/1", NOOP_CALLBACK);
      getAsync(client, "/items/2", NOOP_CALLBACK);
    } finally {
      parent.finish();
    }

    ScopedSpan otherSpan = tracer.startScopedSpan("test2");
    try {
      for (int i = 0; i < 2; i++) {
        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("x-b3-traceId"))
          .isEqualTo(parent.context().traceIdString());
        assertThat(request.getHeader("x-b3-parentspanid"))
          .isEqualTo(parent.context().spanIdString());
      }
    } finally {
      otherSpan.finish();
    }

    // Check we reported 2 in-process spans and 2 RPC client spans
    assertThat(Arrays.asList(takeSpan(), takeSpan(), takeSpan(), takeSpan()))
      .extracting(Span::kind)
      .containsOnly(null, Span.Kind.CLIENT);
  }

  /**
   * This ensures that response callbacks run in the invocation context, not the client one. This
   * allows async chaining to appear caused by the parent, not by the most recent client. Otherwise,
   * we would see a client span child of a client span, which could be confused with duplicate
   * instrumentation and affect dependency link counts.
   */
  @Test public void callbackContextIsFromInvocationTime() throws Exception {
    BlockingQueue<Object> result = new LinkedBlockingQueue<>();
    server.enqueue(new MockResponse());

    ScopedSpan parent = tracer().startScopedSpan("test");
    try {
      getAsync(client, "/foo", new Callback<Void>() {
        @Override public void onSuccess(Void success) {
          result.add(currentTraceContext.get());
        }

        @Override public void onError(Throwable throwable) {
          result.add(throwable);
        }
      });
    } finally {
      parent.finish();
    }
    server.takeRequest();

    assertThat(result.poll(1, TimeUnit.SECONDS))
      .isInstanceOf(TraceContext.class)
      .isSameAs(parent.context());

    // Check we reported 1 in-process span and 1 RPC client spans
    assertThat(Arrays.asList(takeSpan(), takeSpan()))
      .extracting(Span::kind)
      .containsOnly(null, Span.Kind.CLIENT);
  }

  /** This ensures that response callbacks run when there is no invocation trace context. */
  @Test public void asyncRootSpan() throws Exception {
    BlockingQueue<Object> result = new LinkedBlockingQueue<>();
    server.enqueue(new MockResponse());

    getAsync(client, "/foo", new Callback<Void>() {
      @Override public void onSuccess(Void success) {
        result.add(currentTraceContext.get());
      }

      @Override public void onError(Throwable throwable) {
        result.add(throwable);
      }
    });

    server.takeRequest();

    assertThat(result.poll(1, TimeUnit.SECONDS))
      .isNull();

    assertThat(takeSpan().kind())
      .isEqualTo(Span.Kind.CLIENT);
  }
}
