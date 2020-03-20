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

import brave.Tracer;
import brave.Tracing;
import brave.http.HttpTracing;
import brave.internal.Nullable;
import brave.propagation.B3Propagation;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.After;
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
 * This is the base class for http-based integration tests. It has a few features to ensure tests
 * cover common instrumentation bugs. Most of this optimizes for instrumentation occurring on a
 * different thread than main (which does the assertions).
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
 * <h3>This code looks hard.. why are we using a concurrent queue? My http client is easy</h3>
 *
 * <p>Some http client instrumentation are fully synchronous (everything on the main thread).
 * Testing such instrumentation could be easier, ex reporting into a list. Some other race-detecting
 * features may feel overkill in this case.
 *
 * <p>Consider though, this is a base class for all http instrumentation: servers (always report
 * off main thread) and asynchronous clients (often report off main). Also, even blocking clients
 * can execute their "on headers received" hook on a separate thread! Even if the http client you
 * are working on does everything on the same thread, a small change could invalidate that
 * assumption. If something written to work on one thread is suddenly working on two threads, tests
 * can fail "randomly", perhaps not until an unrelated change to JRE. When tests fail, they also
 * make it impossible to release new code until we disable the test or fix it. Bugs or race
 * conditions instrumentation can be very time consuming to solve. For example, they can appear as
 * "flakes" in CI servers such as Travis, which can be near impossible to debug.
 *
 * <p>Bottom-line is that we accept that strict tests are harder up front, and not necessary for a
 * few types of blocking client instrumentation. However, the majority of http instrumentation have
 * to concern themselves with multi-threaded behavior and if we always do, the chances of builds
 * breaking are less.
 */
public abstract class ITHttp {
  /**
   * We use a global rule instead of surefire config as this could be executed in gradle, sbt, etc.
   * This way, there's visibility on which method hung without asking the end users to edit build
   * config.
   */
  @Rule public TestRule globalTimeout = new DisableOnDebug(Timeout.seconds(10)); // max per method
  @Rule public TestName testName = new TestName();

  public static final String EXTRA_KEY = "user-id";

  /**
   * When testing servers or asynchronous clients, spans are reported on a worker thread. In order
   * to read them on the main thread, we use a concurrent queue. As some implementations report
   * after a response is sent, we use a blocking queue to prevent race conditions in tests.
   */
  BlockingQueue<Span> spans = new LinkedBlockingQueue<>();

  /** Call this to block until a span was reported. The span must not have an "error" tag. */
  protected Span takeSpan() throws InterruptedException {
    Span result = doTakeSpan();

    assertThat(result.tags().get("error"))
      .withFailMessage("Expected %s to have no error tag", result)
      .isNull();

    return result;
  }

  /** Like {@link #takeSpan()} except an error tag must be present and match the given value. */
  protected Span takeSpanWithError(String errorTag) throws InterruptedException {
    Span result = doTakeSpan();

    assertThat(result.tags().get("error"))
      .withFailMessage("Expected %s to have an error tag matching %s", result, errorTag)
      .matches(errorTag);

    return result;
  }

  private Span doTakeSpan() throws InterruptedException {
    Span result = spans.poll(3, TimeUnit.SECONDS);
    assertThat(result)
      .withFailMessage("Span was not reported")
      .isNotNull();
    return result;
  }

  protected CurrentTraceContext currentTraceContext = ThreadLocalCurrentTraceContext.newBuilder()
    .addScopeDecorator(StrictScopeDecorator.create())
    .build();
  protected HttpTracing httpTracing;

  protected Tracer tracer() {
    return httpTracing.tracing().tracer();
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

  /**
   * Like {@link #assertSpansReportedKindInOrder(Span.Kind, Span.Kind)} except order isn't enforced.
   * However, the results will return in the kind order specified.
   */
  protected Span[] assertSpansReportedKindInAnyOrder(@Nullable Span.Kind kind1,
    @Nullable Span.Kind kind2) throws InterruptedException {
    if (Objects.equals(kind1, kind2)) {
      throw new AssertionError("Expected test to pass different span kinds");
    }

    // Intentionally pull both spans first to ensure neither are errors.
    Span span1 = takeSpan(), span2 = takeSpan();

    for (Span span : Arrays.asList(span1, span2)) {
      assertThat(span.kind())
        .withFailMessage("Expected %s to have kind=%s or %s", span, kind1, kind2)
        .isIn(kind1, kind2);
    }

    // Now, check the kinds are different
    if (Objects.equals(span1.kind(), span2.kind())) {
      throw new AssertionError("Expected span " + span1 + " to have different kind than " + span2);
    }

    // Sort on the way out so that further assertions make sense.
    Span[] result = new Span[] {span1, span2};
    return result[0].kind() == kind1 ? result : new Span[] {span2, span1};
  }

  protected Span[] assertSpansReportedKindInOrder(@Nullable Span.Kind kind1,
    @Nullable Span.Kind kind2) throws InterruptedException {
    // Intentionally pull both spans first to ensure neither are errors.
    Span span1 = takeSpan(), span2 = takeSpan();

    // First, check if the order is backwards
    if (Objects.equals(span2.kind(), kind1) && Objects.equals(span1.kind(), kind2)) {
      throw new AssertionError("Expected span " + span2 + " to report before span " + span1);
    }

    // Now, check each span
    assertThat(span1.kind())
      .withFailMessage("Expected first %s to have kind=%s", span1, kind1)
      .isEqualTo(kind1);

    assertThat(span2.kind())
      .withFailMessage("Expected second %s to have kind=%s", span2, kind2)
      .isEqualTo(kind2);

    return new Span[] {span1, span2};
  }

  protected Span takeLocalSpan() throws InterruptedException {
    Span local = takeSpan();
    assertThat(local.kind())
      .withFailMessage("Expected %s to have no kind", local)
      .isNull();
    return local;
  }

  protected void assertChildEnclosedByParent(Span child, Span parent) {
    assertThat(parent.traceId())
      .withFailMessage("Expected the same trace ID: %s %s", parent, child)
      .isEqualTo(child.traceId());

    assertThat(parent.id())
      .withFailMessage("Expected %s to be the parent of %s", parent, child)
      .isEqualTo(child.parentId());

    assertThat(parent.timestampAsLong())
      .withFailMessage("Expected %s to start before %s", parent, child)
      .isLessThanOrEqualTo(child.timestampAsLong());

    long childFinishTimeStamp = child.timestampAsLong() + child.durationAsLong();
    long serverFinishTimeStamp = parent.timestampAsLong() + parent.durationAsLong();

    assertThat(childFinishTimeStamp)
      .isGreaterThanOrEqualTo(child.timestampAsLong())
      .isLessThanOrEqualTo(serverFinishTimeStamp);
  }

  protected Tracing.Builder tracingBuilder(Sampler sampler) {
    return Tracing.newBuilder()
      .spanReporter(spans::add)
      .propagationFactory(ExtraFieldPropagation.newFactory(B3Propagation.FACTORY, EXTRA_KEY))
      .currentTraceContext(currentTraceContext)
      .sampler(sampler);
  }
}
