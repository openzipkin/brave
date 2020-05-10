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

import brave.Span.Kind;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

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
 * MutableSpan span = takeLocalSpan();
 * assertThat(span.traceId()).isEqualTo(traceId);
 * }</pre>
 *
 * <em>All spans finished must be taken before the test completes!</em>
 *
 * <h3>Debugging test failures</h3>
 *
 * <p>If a test hangs, likely {@link BlockingQueue#take()} is being called when a span wasn't
 * finished. An exception or bug could cause this (for example, the error handling route not calling
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
 *
 * @see TestSpanHandler
 * @since 5.12
 */
public final class IntegrationTestSpanHandler extends SpanHandler implements TestRule {
  static final String ANY_STRING = ".+";
  /**
   * When testing servers or asynchronous clients, spans are finished on a worker thread. In order
   * to read them on the main thread, we use a concurrent queue. As some implementations report
   * after a response is sent, we use a blocking queue to prevent race conditions in tests.
   */
  BlockingQueue<MutableSpan> spans = new LinkedBlockingQueue<>();
  boolean ignoreAnySpans;

  /**
   * Call this before throwing an {@link AssumptionViolatedException}, when there's a chance a span
   * was finished.
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
   * {@link #doTakeSpan(Throwable, String, String, boolean)} before the end of each method.
   */
  // only check success path to avoid masking assertion errors or exceptions
  void assertSpansConsumed() {
    if (ignoreAnySpans) return;
    try {
      MutableSpan span = spans.poll(100, TimeUnit.MILLISECONDS);
      assertThat(span)
          .withFailMessage("Span remaining in queue. Check for redundant reporting: %s",
              span)
          .isNull();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  /**
   * Blocks until a local span was finished. This does <em>not</em> verify errors.
   */
  public MutableSpan takeLocalSpan() {
    MutableSpan result = doTakeSpan(false);
    assertThat(result.kind())
        .withFailMessage("Expected %s to have no kind", result)
        .isNull();
    assertThat(result.remoteServiceName())
        .withFailMessage("Expected %s to have no remote endpoint", result)
        .isNull();
    return result;
  }

  MutableSpan doTakeSpan(boolean flushed) {
    MutableSpan result;
    try {
      result = spans.poll(3, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }

    assertThat(result)
        .withFailMessage("Timeout waiting for span")
        .isNotNull();

    assertThat(result.startTimestamp())
        .withFailMessage("Expected a startTimestamp: %s", result)
        .isNotZero();

    if (flushed) {
      assertThat(result.finishTimestamp())
          .withFailMessage("Expected no finishTimestamp: %s", result)
          .isZero();
    } else {
      assertThat(result.finishTimestamp())
          .withFailMessage("Expected a finishTimestamp: %s", result)
          .isNotZero();
    }
    return result;
  }

  /**
   * Blocks until a remote span was finished. We define a remote span as one with a timestamp,
   * duration and kind.
   *
   * <p>This will fail if there's an {@link MutableSpan#error()} or an "error" tag. If you expect a
   * failure, use {@link #takeRemoteSpanWithError(Kind, Throwable)} or {@link
   * #takeRemoteSpanWithErrorTag(Kind, String)} instead.
   */
  public MutableSpan takeRemoteSpan(Kind kind) {
    MutableSpan result = doTakeSpan(null, null, null, false);
    assertRemoteSpan(result, kind);
    return result;
  }

  /**
   * Like {@link #takeRemoteSpan(Kind)} except a {@link MutableSpan#error()} must equal the given
   * value.
   *
   * <p><em>Note</em>: This enforces there is no "error" tag. If your framework clarifies the
   * "error" tag when there is also an unhandled exception, use {@link
   * #takeRemoteSpanWithErrorTag(Kind, String)} first, then check for error using normal
   * assertions.
   *
   * @see #takeRemoteSpanWithError(Kind)
   * @see #takeRemoteSpanWithErrorMessage(Kind, String)
   * @see #takeRemoteSpanWithErrorTag(Kind, String)
   */
  public MutableSpan takeRemoteSpanWithError(Kind kind, Throwable error) {
    MutableSpan result = doTakeSpan(error, null, null, false);
    assertRemoteSpan(result, kind);
    return result;
  }

  /**
   * Some frameworks swallow exceptions. This tests that either an "error" tag exists or {@link
   * MutableSpan#error()} does, without regards to the value.
   *
   * @see #takeRemoteSpanWithError(Kind, Throwable)
   * @see #takeRemoteSpanWithErrorMessage(Kind, String)
   * @see #takeRemoteSpanWithErrorTag(Kind, String)
   */
  public MutableSpan takeRemoteSpanWithError(Kind kind) {
    MutableSpan result = doTakeSpan(null, null, ANY_STRING, false);
    assertRemoteSpan(result, kind);
    return result;
  }

  /**
   * Use instead of {@link #takeRemoteSpanWithError(Kind, Throwable)} when you cannot catch a
   * reference to the actual raised exception during a test.
   *
   * <p>This is typically used when testing framework errors result in a finished span.
   *
   * @see #takeRemoteSpanWithError(Kind, Throwable)
   * @see #takeRemoteSpanWithError(Kind)
   * @see #takeRemoteSpanWithErrorTag(Kind, String)
   */
  public MutableSpan takeRemoteSpanWithErrorMessage(Kind kind, String errorMessage) {
    MutableSpan result = doTakeSpan(null, errorMessage, null, false);
    assertRemoteSpan(result, kind);
    return result;
  }

  /**
   * Like {@link #takeRemoteSpan(Kind)} except an error tag must match the given value.
   *
   * @see #takeRemoteSpanWithError(Kind, Throwable)
   * @see #takeRemoteSpanWithError(Kind)
   * @see #takeRemoteSpanWithErrorMessage(Kind, String)
   */
  public MutableSpan takeRemoteSpanWithErrorTag(Kind kind, String errorTag) {
    MutableSpan result = doTakeSpan(null, null, errorTag, false);
    assertRemoteSpan(result, kind);
    return result;
  }

  void assertRemoteSpan(MutableSpan span, Kind kind) {
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
  MutableSpan doTakeSpan(
      @Nullable Throwable error, @Nullable String errorMessage, @Nullable String errorTag,
      boolean flushed) {
    MutableSpan result = doTakeSpan(flushed);

    if (ANY_STRING.equals(errorTag)) { // to save us from yet another parameter
      boolean hasError = result.error() != null || result.tag("error") != null;
      assertThat(hasError)
          .withFailMessage("Expected %s to have an error, but there was no error", result)
          .isTrue();
    } else if (error != null) {
      assertThat(result.error())
          .withFailMessage("Expected %s to have an error, but there was no error", result)
          .isNotNull();

      assertThat(result.error()).isEqualTo(error);
      assertNoErrorTag(result);
    } else if (errorMessage != null) {
      assertThat(result.error())
          .withFailMessage(
              "Expected %s to have an error message matching %s, but there was no error", result,
              errorMessage)
          .isNotNull();

      // Some exception messages are multi-line
      Pattern regex = Pattern.compile(errorMessage, Pattern.DOTALL);
      String actual = result.error().getMessage();
      assertThat(actual)
          .withFailMessage("Expected %s to have an error message matching %s, but was %s", result,
              errorMessage, actual)
          .matches(regex);
      assertNoErrorTag(result);
    } else if (errorTag != null) {
      // Some exception messages are multi-line
      Pattern regex = Pattern.compile(errorTag, Pattern.DOTALL);
      assertThat(result.tags().get("error"))
          .withFailMessage("Expected %s to have an error tag matching %s", result, errorTag)
          .matches(regex);
    } else {
      assertNoError(result);
      assertNoErrorTag(result);
    }

    return result;
  }

  static void assertNoError(MutableSpan result) {
    assertThat(result.error())
        .withFailMessage("Expected %s to have no error", result)
        .isNull();
  }

  // Unhandled exceptions should set span.error(), but not an "error" tag.
  // Otherwise, ZipkinSpanHandler could not differentiate between intended error and not
  static void assertNoErrorTag(MutableSpan result) {
    assertThat(result.tags().get("error"))
        .withFailMessage("Expected %s to have no error tag", result)
        .isNull();
  }

  @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
    if (cause == Cause.ABANDONED) return true;
    spans.add(span);
    return true;
  }

  @Override public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override public void evaluate() throws Throwable {
        try {
          base.evaluate();
          assertSpansConsumed();
        } finally {
          spans.clear();
        }
      }
    };
  }

  @Override public String toString() {
    return spans.toString();
  }
}
