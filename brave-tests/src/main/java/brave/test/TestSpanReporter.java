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
package brave.test;

import brave.internal.Nullable;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This is a special span reporter for remote integration tests. It has a few features to ensure
 * tests cover common instrumentation bugs. Most of this optimizes for instrumentation occurring on
 * a different thread than main (which does the assertions).
 *
 * <p><pre><ul>
 *   <li>Spans report into a concurrent blocking queue to prevent assertions race conditions</li>
 *   <li>After tests complete, the queue is strictly checked to catch redundant span reporting</li>
 * </ul></pre>
 *
 * <p>As a blocking queue is used, {@linkplain #takeLocalSpan()} take a span} to perform assertions
 * on it.
 *
 * <pre>{@code
 * Span span = takeLocalSpan();
 * assertThat(span.traceId()).isEqualTo(traceId);
 * }</pre>
 *
 * <em>All spans reported must be taken before the test completes!</em>
 *
 * <h3>Debugging test failures</h3>
 *
 * <p>If a test hangs, likely {@link BlockingQueue#take()} is being called when a span wasn't
 * reported. An exception or bug could cause this (for example, the error handling route not calling
 * {@link brave.Span#finish()}).
 *
 * <p>If a test fails on {@link After}, it can mean that your test created a span, but didn't
 * {@link BlockingQueue#take()} it off the queue. If you are testing something that creates a span,
 * you may not want to verify each one. In this case, at least take them similar to below:
 *
 * <p><pre>{@code
 * for (int i = 0; i < 10; i++) takeLocalSpan(); // we expected 10 spans
 * }</pre>
 *
 * <h3>This code looks hard.. why are we using a concurrent queue? My client is easy</h3>
 *
 * <p>Some client instrumentation are fully synchronous (everything on the main thread).
 * Testing such instrumentation could be easier, ex reporting into a list. Some other race-detecting
 * features may feel overkill in this case.
 *
 * <p>Consider though, this is a base class for all remote instrumentation: servers (always report
 * off main thread) and asynchronous clients (often report off main). Also, even blocking clients
 * can execute their "on headers received" hook on a separate thread! Even if the client you are
 * working on does everything on the same thread, a small change could invalidate that assumption.
 * If something written to work on one thread is suddenly working on two threads, tests can fail
 * "randomly", perhaps not until an unrelated change to JRE. When tests fail, they also make it
 * impossible to release new code until we disable the test or fix it. Bugs or race conditions
 * instrumentation can be very time consuming to solve. For example, they can appear as "flakes" in
 * CI servers such as Travis, which can be near impossible to debug.
 *
 * <p>Bottom-line is that we accept that strict tests are harder up front, and not necessary for a
 * few types of blocking client instrumentation. However, the majority of remote instrumentation
 * have to concern themselves with multi-threaded behavior and if we always do, the chances of
 * builds breaking are less.
 */
public final class TestSpanReporter extends TestWatcher implements Reporter<Span> {
  /**
   * When testing servers or asynchronous clients, spans are reported on a worker thread. In order
   * to read them on the main thread, we use a concurrent queue. As some implementations report
   * after a response is sent, we use a blocking queue to prevent race conditions in tests.
   */
  BlockingQueue<Span> spans = new LinkedBlockingQueue<>();
  boolean ignoreAnySpans;

  /**
   * Call this before throwing an {@link AssumptionViolatedException}, when there's a chance a span
   * was reported.
   *
   * <p>This was made for detecting features in HTTP server testing via 404. When 404 itself is
   * instrumented, post-conditions would otherwise fail from not consuming the associated span.
   */
  public void ignoreAnySpans() {
    ignoreAnySpans = true;
  }

  /**
   * On close, we check that all spans have been verified by the test. This ensures bad behavior
   * such as duplicate reporting doesn't occur. The impact is that every span must at least be
   * {@link #doTakeSpan(String, boolean)} before the end of each method.
   */
  // only check success path to avoid masking assertion errors or exceptions
  @Override protected void succeeded(Description description) {
    if (ignoreAnySpans) return;
    try {
      assertThat(spans.poll(100, TimeUnit.MILLISECONDS))
        .withFailMessage("Span remaining in queue. Check for redundant reporting")
        .isNull();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  /**
   * Blocks until a local span was reported. We define a local span as one with a timestamp and no
   * duration, kind, or remote endpoint. This will fail if there's an "error" tag. If you expect a
   * failure, use {@link #takeLocalSpanWithError(String)} instead.
   */
  public Span takeLocalSpan() {
    Span local = doTakeSpan(null, false);
    assertLocalSpan(local);
    return local;
  }

  /** Like {@link #takeLocalSpan()} except an error tag must match the given value. */
  public Span takeLocalSpanWithError(String errorTag) {
    Span result = doTakeSpan(errorTag, false);
    assertLocalSpan(result);
    return result;
  }

  private void assertLocalSpan(Span local) {
    assertThat(local.kind())
      .withFailMessage("Expected %s to have no kind", local)
      .isNull();
    assertThat(local.remoteEndpoint())
      .withFailMessage("Expected %s to have no remote endpoint", local)
      .isNull();
  }

  /**
   * Blocks until a remote span was reported. We define a remote span as one with a timestamp,
   * duration and kind. This will fail if there's an "error" tag. If you expect a failure, use
   * {@link #takeRemoteSpanWithError(Span.Kind, String)} instead.
   */
  public Span takeRemoteSpan(Span.Kind kind) {
    Span result = doTakeSpan(null, false);
    assertRemoteSpan(result, kind);
    return result;
  }

  /** Like {@link #takeRemoteSpan(Span.Kind)} except an error tag must match the given value. */
  public Span takeRemoteSpanWithError(Span.Kind kind, String errorTag) {
    Span result = doTakeSpan(errorTag, false);
    assertRemoteSpan(result, kind);
    return result;
  }

  void assertRemoteSpan(Span span, Span.Kind kind) {
    assertThat(span.kind())
      .withFailMessage("Expected %s to have kind=%s", span, kind)
      .isEqualTo(kind);
  }

  /**
   * This is hidden because the historical takeSpan() method led to numerous bugs. For example,
   * tests passed even though the span asserted against wasn't the intended one (local vs remote).
   * Also, tests passed even though there was an error inside the span. Even error tests have passed
   * for the wrong reason (ex setup failure not the error raised in instrumentation).
   */
  Span doTakeSpan(@Nullable String errorTag, boolean flushed) {
    Span result;
    try {
      result = spans.poll(3, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }

    assertThat(result)
      .withFailMessage("Span was not reported")
      .isNotNull();

    assertThat(result.timestampAsLong())
      .withFailMessage("Expected a timestamp: %s", result)
      .isNotZero();

    if (errorTag != null) {
      // Some exception messages are multi-line
      Pattern regex = Pattern.compile(errorTag, Pattern.DOTALL);
      assertThat(result.tags().get("error"))
        .withFailMessage("Expected %s to have an error tag matching %s", result, errorTag)
        .matches(regex);
    } else {
      assertThat(result.tags().get("error"))
        .withFailMessage("Expected %s to have no error tag", result)
        .isNull();
    }

    if (flushed) {
      assertThat(result.durationAsLong())
        .withFailMessage("Expected no duration: %s", result)
        .isZero();
    } else {
      assertThat(result.durationAsLong())
        .withFailMessage("Expected a duration: %s", result)
        .isNotZero();
    }

    return result;
  }

  @Override public void report(Span span) {
    spans.add(span);
  }
}
