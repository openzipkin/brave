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

import brave.Span;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.rules.TestRule;

/**
 * Simpler variant of {@link IntegrationTestSpanHandler} appropriate for single-threaded
 * unit-tests.
 *
 * <p>Ex.
 * <pre>{@code
 * TestSpanHandler spans = new TestSpanHandler();
 * Tracing tracing = Tracing.newBuilder().addSpanHandler(spans).build();
 *
 * @After public void close() {
 *   tracing.close();
 * }
 *
 * @Test public void test() {
 *   tracing.tracer().startScopedSpan("foo").finish();
 *
 *   assertThat(spans.get(0).name())
 *     .isEqualTo("foo");
 * }
 * }</pre>
 *
 * <h3>Comparison with {@link IntegrationTestSpanHandler}</h3>
 * It is possible to use this type in multi-threaded tests, but there are usually problems that
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
 * IntegrationTestSpanHandler} is a {@link TestRule}, which verifies all spans are accounted for.
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
