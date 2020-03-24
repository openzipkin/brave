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

import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.test.util.AssertableCallback;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.MockResponse;
import org.junit.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class ITHttpAsyncClient<C> extends ITHttpClient<C> {
  /**
   * This invokes a GET with the indicated path, but does not block until the response is complete.
   *
   * <p>The success callback should always be invoked with the HTTP status code. If the
   * implementation coerces a 500 code without an exception as an error, you should call the success
   * callback directly.
   *
   * <p>One of success or failure callbacks must be invoked even on unexpected scenarios. For
   * example, if there is a cancelation that didn't result in an error, invoke {@link
   * AssertableCallback#onError(Throwable)} with your own {@link CancellationException}.
   */
  protected abstract void getAsync(C client, String path, AssertableCallback<Integer> callback);

  /**
   * This tests that the parent is determined at the time the request was made, not when the request
   * was executed.
   */
  @Test public void usesParentFromInvocationTime() {
    server.enqueue(new MockResponse().setBodyDelay(300, TimeUnit.MILLISECONDS));
    server.enqueue(new MockResponse());

    AssertableCallback<Integer> items1 = new AssertableCallback<>();
    AssertableCallback<Integer> items2 = new AssertableCallback<>();

    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      getAsync(client, "/items/1", items1);
      getAsync(client, "/items/2", items2);
    }

    try (Scope scope = currentTraceContext.newScope(null)) {
      // complete within a different scope
      items1.join();
      items2.join();

      for (int i = 0; i < 2; i++) {
        TraceContext extracted = extract(takeRequest());
        assertChildOf(extracted, parent);
      }
    }

    // The spans may report in a different order than the requests
    for (int i = 0; i < 2; i++) {
      assertChildOf(reporter.takeRemoteSpan(Span.Kind.CLIENT), parent);
    }
  }

  /**
   * This ensures that response callbacks run in the invocation context, not the client one. This
   * allows async chaining to appear caused by the parent, not by the most recent client. Otherwise,
   * we would see a client span child of a client span, which could be confused with duplicate
   * instrumentation and affect dependency link counts.
   */
  @Test public void callbackContextIsFromInvocationTime() {
    server.enqueue(new MockResponse());

    AssertableCallback<Integer> callback = new AssertableCallback<>();

    // Capture the current trace context when onSuccess or onError occur
    AtomicReference<TraceContext> invocationContext = new AtomicReference<>();
    callback.setListener(() -> invocationContext.set(currentTraceContext.get()));

    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      getAsync(client, "/foo", callback);
    }

    callback.join(); // ensures listener ran
    assertThat(invocationContext.get()).isSameAs(parent);
    assertChildOf(reporter.takeRemoteSpan(Span.Kind.CLIENT), parent);
  }

  /** This ensures that response callbacks run when there is no invocation trace context. */
  @Test public void callbackContextIsFromInvocationTime_root() {
    server.enqueue(new MockResponse());

    AssertableCallback<Integer> callback = new AssertableCallback<>();

    // Capture the current trace context when onSuccess or onError occur
    AtomicReference<TraceContext> invocationContext = new AtomicReference<>();
    callback.setListener(() -> invocationContext.set(currentTraceContext.get()));

    getAsync(client, "/foo", callback);

    callback.join(); // ensures listener ran
    assertThat(invocationContext.get()).isNull();
    assertThat(reporter.takeRemoteSpan(Span.Kind.CLIENT).parentId()).isNull();
  }

  @Test public void addsStatusCodeWhenNotOk_async() {
    AssertableCallback<Integer> callback = new AssertableCallback<>();
    int expectedStatusCode = 400;
    server.enqueue(new MockResponse().setResponseCode(expectedStatusCode));

    getAsync(client, "/foo", callback);

    takeRequest();

    // Ensure the getAsync() method is implemented correctly
    callback.join();

    assertThat(reporter.takeRemoteSpanWithError(Span.Kind.CLIENT, "400").tags())
      .containsEntry("http.status_code", "400");
  }
}
