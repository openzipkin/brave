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

import brave.Tracing;
import brave.propagation.B3Propagation;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.Propagation;
import brave.propagation.SamplingFlags;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.Rule;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.rules.Timeout;
import org.junit.runner.Description;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This is the base class for remote integration tests. It has a few features to ensure tests cover
 * common instrumentation bugs. Most of this optimizes for instrumentation occurring on a different
 * thread than main (which does the assertions).
 *
 * <p><pre><ul>
 *   <li>{@link StrictScopeDecorator} double-checks threads don't leak contexts</li>
 *   <li>Span reporting double-checks the span was de-scoped on finish, to prevent leaks</li>
 *   <li>Spans report into a concurrent blocking queue to prevent assertions race conditions</li>
 *   <li>After tests complete, the queue is strictly checked to catch redundant span reporting</li>
 * </ul></pre>
 *
 * <p>As a blocking queue is used, {@linkplain #takeSpan() take a span} to perform assertions on
 * it.
 *
 * <pre>{@code
 * Span span = takeSpan();
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
 * for (int i = 0; i < 10; i++) takeSpan(); // we expected 10 spans
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
public abstract class ITRemote {
  /**
   * We use a global rule instead of surefire config as this could be executed in gradle, sbt, etc.
   * This way, there's visibility on which method hung without asking the end users to edit build
   * config.
   *
   * <p>Normal tests will pass in less than 5 seconds. This timeout is set to 20 to be higher than
   * needed even in a an overloaded CI server or extreme garbage collection pause.
   */
  @Rule public TestRule globalTimeout = new DisableOnDebug(Timeout.seconds(20)); // max per method
  @Rule public TestName testName = new TestName();

  public static final String EXTRA_KEY = "user-id";

  /** Returns a parent context for use in propagation tests. */
  protected TraceContext newParentContext(SamplingFlags flags) {
    TraceContext result = TraceContext.newBuilder()
      .traceIdHigh(1L).traceId(2L).parentId(3L).spanId(1L)
      .sampled(flags.sampled())
      .sampledLocal(flags.sampledLocal())
      .debug(flags.debug()).build();

    return tracing.propagationFactory().decorate(result);
  }

  /**
   * When testing servers or asynchronous clients, spans are reported on a worker thread. In order
   * to read them on the main thread, we use a concurrent queue. As some implementations report
   * after a response is sent, we use a blocking queue to prevent race conditions in tests.
   */
  BlockingQueue<Span> spans = new LinkedBlockingQueue<>();
  boolean ignoreAnySpans;

  // field because this allows subclasses to initialize a field Tracing
  protected final CurrentTraceContext currentTraceContext =
    ThreadLocalCurrentTraceContext.newBuilder()
      .addScopeDecorator(StrictScopeDecorator.create())
      .build();

  protected final Propagation.Factory propagationFactory =
    ExtraFieldPropagation.newFactory(B3Propagation.FACTORY, EXTRA_KEY);

  protected Tracing tracing = tracingBuilder(Sampler.ALWAYS_SAMPLE).build();

  /** Call this to block until a span was reported. The span must not have an "error" tag. */
  protected Span takeSpan() throws InterruptedException {
    Span result = doTakeSpan(false);

    assertThat(result.tags().get("error"))
      .withFailMessage("Expected %s to have no error tag", result)
      .isNull();

    return result;
  }

  /** Similar to {@link #takeSpan()}, except requires {@link Span#durationAsLong()} to be zero. */
  protected Span takeFlushedSpan() throws InterruptedException {
    Span result = doTakeSpan(true);

    assertThat(result.tags().get("error"))
      .withFailMessage("Expected %s to have no error tag", result)
      .isNull();

    return result;
  }

  /** Like {@link #takeSpan()} except an error tag must be present and match the given value. */
  protected Span takeSpanWithError(String errorTag) throws InterruptedException {
    Span result = doTakeSpan(false);

    // Some exception messages are multi-line
    Pattern regex = Pattern.compile(errorTag, Pattern.DOTALL);
    assertThat(result.tags().get("error"))
      .withFailMessage("Expected %s to have an error tag matching %s", result, errorTag)
      .matches(regex);

    return result;
  }

  /**
   * Call this before throwing an {@link AssumptionViolatedException}, when there's a chance a span
   * was reported.
   *
   * <p>This was made for detecting features in HTTP server testing via 404. When 404 itself is
   * instrumented, post-conditions would otherwise fail from not consuming the associated span.
   */
  protected void ignoreAnySpans() {
    ignoreAnySpans = true;
  }

  private Span doTakeSpan(boolean flushed) throws InterruptedException {
    Span result = spans.poll(3, TimeUnit.SECONDS);
    assertThat(result)
      .withFailMessage("Span was not reported")
      .isNotNull();

    assertThat(result.timestampAsLong())
      .withFailMessage("Expected a timestamp: %s", result)
      .isNotZero();

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

  /**
   * This closes the current instance of tracing, to prevent it from being accidentally visible to
   * other test classes which call {@link Tracing#current()}.
   */
  @After public void close() throws Exception {
    Tracing current = Tracing.current();
    if (current != null) current.close();
  }

  /**
   * On close, we check that all spans have been verified by the test. This ensures bad behavior
   * such as duplicate reporting doesn't occur. The impact is that every span must at least be
   * {@link #takeSpan()} taken} before the end of each method.
   */
  @Rule public TestRule assertSpansEmpty = new TestWatcher() {
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
  };

  protected Span takeLocalSpan() throws InterruptedException {
    Span local = takeSpan();
    assertThat(local.kind())
      .withFailMessage("Expected %s to have no kind", local)
      .isNull();
    return local;
  }

  /** Call this to block until a remote span was reported. The span must not have an "error" tag. */
  protected Span takeRemoteSpan(Span.Kind kind) throws InterruptedException {
    Span result = takeSpan();
    assertRemoteSpan(result, kind);
    return result;
  }

  /** Like {@link #takeRemoteSpan(Span.Kind)} except an error tag must match the given value. */
  protected Span takeRemoteSpanWithError(Span.Kind kind, String errorTag)
    throws InterruptedException {
    Span result = takeSpanWithError(errorTag);
    assertRemoteSpan(result, kind);
    return result;
  }

  void assertRemoteSpan(Span span, Span.Kind kind) {
    assertThat(span.kind())
      .withFailMessage("Expected %s to have kind=%s", span, kind)
      .isEqualTo(kind);
  }

  /**
   * Ensures the inputs are parent and child, the parent starts before the child, and the duration
   * of the child is inside the parent's duration.
   */
  protected void assertSpanInInterval(Span span, long beginTimestamp, long endTimestamp) {
    assertThat(span.timestampAsLong())
      .withFailMessage("Expected %s to start after %s", span, beginTimestamp)
      .isGreaterThanOrEqualTo(beginTimestamp);

    assertThat(span.timestampAsLong() + span.durationAsLong())
      .withFailMessage("Expected %s to finish after %s", span, endTimestamp)
      .isLessThanOrEqualTo(endTimestamp);
  }

  /** Ensures the first finished before the other started. */
  protected void assertSequential(Span span1, Span span2) {
    assertThat(span1.id())
      .withFailMessage("Expected different span IDs: %s %s", span1, span2)
      .isNotEqualTo(span2.id());

    long span1FinishTimeStamp = span1.timestampAsLong() + span1.durationAsLong();

    assertThat(span1FinishTimeStamp)
      .withFailMessage("Expected %s to finish before %s started", span1, span2)
      .isLessThanOrEqualTo(span2.timestampAsLong());
  }

  /**
   * Useful for checking {@linkplain Span.Kind#SERVER server} spans when {@link
   * Tracing.Builder#supportsJoin(boolean)}.
   */
  protected void assertSameIds(Span span, TraceContext parent) {
    assertThat(span.traceId())
      .withFailMessage("Expected to have trace ID(%s): %s", parent.traceIdString(), span)
      .isEqualTo(parent.traceIdString());

    assertThat(span.parentId())
      .withFailMessage("Expected to have parent ID(%s): %s", parent.parentIdString(), span)
      .isEqualTo(parent.parentIdString());

    assertThat(span.id())
      .withFailMessage("Expected to have span ID(%s): %s", parent.spanIdString(), span)
      .isEqualTo(parent.spanIdString());
  }

  protected void assertChildOf(TraceContext child, TraceContext parent) {
    assertChildOf(Span.newBuilder().traceId(child.traceIdString())
      .parentId(child.parentIdString())
      .id(child.spanIdString())
      .build(), parent);
  }

  protected void assertChildOf(Span child, TraceContext parent) {
    assertChildOf(child, Span.newBuilder().traceId(parent.traceIdString())
      .parentId(parent.parentIdString())
      .id(parent.spanIdString())
      .build());
  }

  protected void assertChildOf(Span child, Span parent) {
    assertThat(child.traceId())
      .withFailMessage("Expected to have trace ID(%s): %s", parent.traceId(), child)
      .isEqualTo(parent.traceId());

    assertThat(child.parentId())
      .withFailMessage("Expected to have parent ID(%s): %s", parent.id(), child)
      .isEqualTo(parent.id());
  }

  protected Tracing.Builder tracingBuilder(Sampler sampler) {
    return Tracing.newBuilder()
      .spanReporter(spans::add)
      .propagationFactory(propagationFactory)
      .currentTraceContext(currentTraceContext)
      .sampler(sampler);
  }
}
