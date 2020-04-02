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
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.Propagation;
import brave.propagation.SamplingFlags;
import brave.propagation.StrictCurrentTraceContext;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This is the base class for remote integration tests. It has a few features to ensure tests cover
 * common instrumentation bugs. Most of this optimizes for instrumentation occurring on a different
 * thread than main (which does the assertions).
 *
 * <p><pre><ul>
 *   <li>{@link StrictCurrentTraceContext} double-checks threads don't leak contexts</li>
 *   <li>{@link TestSpanReporter} helps avoid race conditions or accidental errors</li>
 * </ul></pre>
 */
public abstract class ITRemote {
  public static final String EXTRA_KEY = "user-id";

  /**
   * We use a global rule instead of surefire config as this could be executed in gradle, sbt, etc.
   * This way, there's visibility on which method hung without asking the end users to edit build
   * config.
   *
   * <p>Normal tests will pass in less than 5 seconds. This timeout is set to 20 to be higher than
   * needed even in a an overloaded CI server or extreme garbage collection pause.
   */
  @Rule public TestRule globalTimeout = new DisableOnDebug(Timeout.seconds(20)); // max per method
  @Rule public TestSpanReporter reporter = new TestSpanReporter();
  @Rule public TestName testName = new TestName();

  /** Returns a trace context for use in propagation tests. */
  protected TraceContext newTraceContext(SamplingFlags flags) {
    long id = System.nanoTime(); // Random enough as tests are run serially anyway
    TraceContext result = TraceContext.newBuilder()
      .traceIdHigh(id).traceId(id + 1).parentId(id + 2).spanId(id + 3)
      .sampled(flags.sampled())
      .sampledLocal(flags.sampledLocal())
      .debug(flags.debug()).build();

    return tracing.propagationFactory().decorate(result);
  }

  // field because this allows subclasses to initialize a field Tracing
  protected final StrictCurrentTraceContext currentTraceContext = new StrictCurrentTraceContext();

  protected final Propagation.Factory propagationFactory =
    ExtraFieldPropagation.newFactory(B3Propagation.FACTORY, EXTRA_KEY);

  protected Tracing tracing = tracingBuilder(Sampler.ALWAYS_SAMPLE).build();

  protected Tracing.Builder tracingBuilder(Sampler sampler) {
    return Tracing.newBuilder()
      .spanReporter(reporter)
      .propagationFactory(propagationFactory)
      .currentTraceContext(currentTraceContext)
      .sampler(sampler);
  }

  /**
   * This closes the current instance of tracing, to prevent it from being accidentally visible to
   * other test classes which call {@link Tracing#current()}.
   *
   * <p>This also checks for scope leaks. It is important that you have closed all resources prior
   * to this method call. Otherwise, in-flight request cleanup may be mistaken for scope leaks. This
   * may involve blocking on completion, if using executors.
   *
   * <p>Ex.
   * <pre>{@code
   * @After @Override public void close() throws Exception {
   *   executorService.shutdown();
   *   executorService.awaitTermination(1, TimeUnit.SECONDS);
   *   super.close();
   * }
   * }</pre>
   */
  @After public void close() throws Exception {
    Tracing current = Tracing.current();
    if (current != null) current.close();
    checkForLeakedScopes();
  }

  /** Override to control scope leak enforcement. */
  protected void checkForLeakedScopes() {
    currentTraceContext.close();
  }

  // Assertions below here can eventually move to a new type

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
}
