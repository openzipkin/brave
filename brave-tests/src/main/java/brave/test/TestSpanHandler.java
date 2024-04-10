/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.test;

import brave.Span;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.extension.Extension;

/**
 * Simpler variant of {@link IntegrationTestSpanHandler} appropriate for single-threaded
 * unit-tests.
 *
 * <p>Ex.
 * <pre>{@code
 * TestSpanHandler spans = new TestSpanHandler();
 * Tracing tracing = Tracing.newBuilder().addSpanHandler(spans).build();
 *
 * @AfterEach void close() {
 *   tracing.close();
 * }
 *
 * @Test void test() {
 *   tracing.tracer().startScopedSpan("foo").finish();
 *
 *   assertThat(spans.get(0).name())
 *     .isEqualTo("foo");
 * }
 * }</pre>
 *
 * <h3>Comparison with {@link IntegrationTestSpanHandler}</h3>
 * It is possible to use this type in multithreaded tests, but there are usually problems that
 * arise better solved by {@link IntegrationTestSpanHandler}. Here are a few examples.
 *
 * <p>Multi-threaded tests typically end up with timing issues which can lead to broken builds (aka
 * "flakey tests"). These are sometimes mitigated by polling assertions such as <a
 * href="https://github.com/awaitility/awaitility">Awaitility</a>.
 *
 * <p>Even with polling assertions, multi-threaded tests have more error cases. Historically, we
 * found tests passing even in error because people only checked the name. {@link
 * IntegrationTestSpanHandler} prevents silent errors from passing.
 *
 * <p>Usually, multi-threaded tests involve remote spans. {@link IntegrationTestSpanHandler} has
 * utilities made for remote spans, such as {@link IntegrationTestSpanHandler#takeRemoteSpan(Span.Kind)}.
 *
 * <p>It is a common instrumentation bug to accidentally create redundant spans. {@link
 * IntegrationTestSpanHandler} is an {@link Extension}, which verifies all spans are accounted for.
 *
 * @see IntegrationTestSpanHandler
 * @since 5.12
 */
public final class TestSpanHandler extends SpanHandler implements Iterable<MutableSpan> {
  // Synchronized not to discourage IntegrationTestSpanHandler when it should be used.
  // Rather, this allows iterative conversion of test code from custom Zipkin reporters to Brave.
  final List<MutableSpan> spans = new ArrayList<>(); // guarded by itself

  public MutableSpan get(int i) {
    synchronized (spans) {
      return spans.get(i);
    }
  }

  public List<MutableSpan> spans() {
    synchronized (spans) { // avoid Iterator pitfalls noted in Collections.synchronizedList
      return new ArrayList<>(spans);
    }
  }

  @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
    synchronized (spans) {
      spans.add(span);
    }
    return true;
  }

  @Override public Iterator<MutableSpan> iterator() {
    return spans().iterator();
  }

  public void clear() {
    synchronized (spans) {
      spans.clear();
    }
  }

  @Override public String toString() {
    return "TestSpanHandler{" + spans() + "}";
  }
}
