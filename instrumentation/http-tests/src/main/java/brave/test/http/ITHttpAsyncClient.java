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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Test;
import zipkin2.Callback;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class ITHttpAsyncClient<C> extends ITHttpClient<C> {
  static final Callback<Integer> LOG_ON_ERROR_CALLBACK = new Callback<Integer>() {
    @Override public void onSuccess(Integer statusCode) {
    }

    @Override public void onError(Throwable throwable) {
      Logger.getAnonymousLogger().log(Level.SEVERE, "Unexpected error", throwable);
    }
  };

  // LinkedBlockingQueue doesn't allow nulls. Use a null sentinel as opposed to crashing the
  // callback thread which would cause result.poll() to await max time.
  Object nullSentinel = new Object();

  /**
   * This invokes a GET with the indicated path, but does not block until the response is complete.
   *
   * <p>The success callback should always be invoked with the HTTP status code. If the
   * implementation coerces a 500 code without an exception as an error, you should call the success
   * callback directly.
   *
   * <p>One of success or failure callbacks must be invoked even on unexpected scenarios. For
   * example, if there is a cancelation that didn't issue an error callback directly, you should
   * invoke the {@link Callback#onError(Throwable)} with your own {@link CancellationException}.
   */
  protected abstract void getAsync(C client, String path, Callback<Integer> callback)
    throws Exception;

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
      getAsync(client, "/items/1", LOG_ON_ERROR_CALLBACK);
      getAsync(client, "/items/2", LOG_ON_ERROR_CALLBACK);
    } finally {
      parent.finish();
    }
    takeLocalSpan();

    ScopedSpan otherSpan = tracer.startScopedSpan("test2");
    try {
      for (int i = 0; i < 2; i++) {
        RecordedRequest request = takeRequest();
        assertThat(request.getHeader("x-b3-traceId"))
          .isEqualTo(parent.context().traceIdString());
        assertThat(request.getHeader("x-b3-parentspanid"))
          .isEqualTo(parent.context().spanIdString());
        takeClientSpan();
      }
    } finally {
      otherSpan.finish();
    }
    takeLocalSpan();
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
      getAsync(client, "/foo", new Callback<Integer>() {
        @Override public void onSuccess(Integer statusCode) {
          result.add(currentTraceContext.get());
        }

        @Override public void onError(Throwable throwable) {
          result.add(throwable);
        }
      });
    } finally {
      parent.finish();
    }

    takeRequest();

    assertThat(result.poll(1, TimeUnit.SECONDS))
      .isInstanceOf(TraceContext.class)
      .isSameAs(parent.context());

    assertSpansReportedKindInAnyOrder(null, Span.Kind.CLIENT);
  }

  /** This ensures that response callbacks run when there is no invocation trace context. */
  @Test public void callbackContextIsFromInvocationTime_root() throws Exception {
    BlockingQueue<Object> result = new LinkedBlockingQueue<>();
    server.enqueue(new MockResponse());

    getAsync(client, "/foo", new Callback<Integer>() {
      @Override public void onSuccess(Integer statusCode) {
        TraceContext context = currentTraceContext.get();
        result.add(context != null ? context : nullSentinel);
      }

      @Override public void onError(Throwable throwable) {
        result.add(throwable);
      }
    });

    takeRequest();

    assertThat(result.poll(1, TimeUnit.SECONDS))
      .isSameAs(nullSentinel);

    takeClientSpan();
  }

  @Test public void addsStatusCodeWhenNotOk_async() throws Exception {
    BlockingQueue<Object> result = new LinkedBlockingQueue<>();
    int expectedStatusCode = 400;
    server.enqueue(new MockResponse().setResponseCode(expectedStatusCode));

    getAsync(client, "/foo", new Callback<Integer>() {
      @Override public void onSuccess(Integer statusCode) {
        result.add(statusCode != null ? statusCode : nullSentinel);
      }

      @Override public void onError(Throwable throwable) {
        result.add(throwable);
      }
    });

    takeRequest();

    // Ensure the getAsync() method is implemented correctly
    Object object = result.poll(1, TimeUnit.SECONDS);
    assertThat(object)
      .isInstanceOf(Integer.class)
      .isNotSameAs(nullSentinel)
      .isEqualTo(expectedStatusCode);

    String expectedStatusCodeString = String.valueOf(expectedStatusCode);

    assertThat(takeClientSpanWithError(expectedStatusCodeString).tags())
      .containsEntry("http.status_code", expectedStatusCodeString);
  }
}
