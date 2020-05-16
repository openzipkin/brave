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
import brave.Tracing;
import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import brave.handler.MutableSpan;
import brave.internal.InternalPropagation;
import brave.internal.Platform;
import brave.internal.handler.OrphanTracker;
import brave.propagation.B3Propagation;
import brave.propagation.CurrentTraceContext;
import brave.propagation.Propagation;
import brave.propagation.SamplingFlags;
import brave.propagation.StrictCurrentTraceContext;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.logging.Level;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import static brave.internal.InternalPropagation.FLAG_LOCAL_ROOT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * This is the base class for remote integration tests. It has a few features to ensure tests cover
 * common instrumentation bugs. Most of this optimizes for instrumentation occurring on a different
 * thread than main (which does the assertions).
 *
 * <p><pre><ul>
 *   <li>{@link StrictCurrentTraceContext} double-checks threads don't leak contexts</li>
 *   <li>{@link IntegrationTestSpanHandler} helps avoid race conditions or accidental errors</li>
 * </ul></pre>
 */
public abstract class ITRemote {
  static {
    SamplingFlags.NOT_SAMPLED.toString(); // ensure InternalPropagation is wired for tests
  }

  public static final BaggageField BAGGAGE_FIELD = BaggageField.create("userId");
  /**
   * Sets a {@linkplain Propagation#keys() propagation key} for {@link #BAGGAGE_FIELD}, which can
   * work with JMS (that prohibits the '-' character).
   *
   * <p><em>Note</em>: If we didn't do this, it would be propagated all lowercase, ex "userid",
   * which is harmless. We reset this for two reasons:
   *
   * <p><ul>
   * <li>Ensures {@link SingleBaggageField#keyNames()} are used on the wire, instead of {@link
   * BaggageField#name()}</li>
   * <li>Warn maintainers about JMS related naming choices.</li>
   * </ul>
   */
  public static final String BAGGAGE_FIELD_KEY = "user_id";

  /**
   * We use a global rule instead of surefire config as this could be executed in gradle, sbt, etc.
   * This way, there's visibility on which method hung without asking the end users to edit build
   * config.
   *
   * <p>Normal tests will pass in less than 5 seconds. This timeout is set to 20 to be higher than
   * needed even in a an overloaded CI server or extreme garbage collection pause.
   */
  @Rule public TestRule globalTimeout = new DisableOnDebug(Timeout.seconds(20)); // max per method
  @Rule public IntegrationTestSpanHandler spanHandler = new IntegrationTestSpanHandler();
  @Rule public TestName testName = new TestName();

  /** Returns a trace context for use in propagation tests. */
  protected TraceContext newTraceContext(SamplingFlags flags) {
    long id = System.nanoTime(); // Random enough as tests are run serially anyway

    // Simulate a new local root root, but without the dependency on Tracer to create it.
    TraceContext context = InternalPropagation.instance.newTraceContext(
      InternalPropagation.instance.flags(flags) | FLAG_LOCAL_ROOT,
      0L,
      id + 1L,
      id + 3L,
      id + 2L,
      id + 3L,
      Collections.emptyList()
    );

    return propagationFactory.decorate(context);
  }

  protected final CurrentTraceContext currentTraceContext;
  protected final Propagation.Factory propagationFactory;
  protected Tracing tracing; // mutable for test-specific configuration!

  final MutableSpan defaultSpan;
  final Closeable checkForLeakedScopes; // internal to this type

  /** Subclass to override the builder. The result will have {@link StrictScopeDecorator} added */
  protected CurrentTraceContext.Builder currentTraceContextBuilder() {
    return StrictCurrentTraceContext.newBuilder();
  }

  protected ITRemote() {
    CurrentTraceContext.Builder currentTraceContextBuilder = currentTraceContextBuilder();
    if (currentTraceContextBuilder instanceof StrictCurrentTraceContext.Builder) {
      currentTraceContext = currentTraceContextBuilder.build();
      checkForLeakedScopes = (Closeable) currentTraceContext;
    } else {
      StrictScopeDecorator strictScopeDecorator = StrictScopeDecorator.create();
      currentTraceContext = currentTraceContextBuilder
        .addScopeDecorator(strictScopeDecorator).build();
      checkForLeakedScopes = strictScopeDecorator;
    }
    propagationFactory = BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
      .add(SingleBaggageField.newBuilder(BAGGAGE_FIELD)
        .addKeyName(BAGGAGE_FIELD_KEY)
        .build()).build();
    defaultSpan = new MutableSpan();
    defaultSpan.localServiceName(getClass().getSimpleName());
    defaultSpan.localIp("127.0.0.1"); // Prevent implicit lookups
    tracing = tracingBuilder(Sampler.ALWAYS_SAMPLE).build();
  }

  protected Tracing.Builder tracingBuilder(Sampler sampler) {
    return Tracing.newBuilder()
      .localServiceName(defaultSpan.localServiceName())
      .localIp(defaultSpan.localIp())
      .addSpanHandler(OrphanTracker.newBuilder()
        .defaultSpan(defaultSpan)
        .clock(Platform.get().clock())
        .logLevel(Level.WARNING) // Default is FINE: invisible in CI.
        .build())
      .addSpanHandler(spanHandler)
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

  /** Override to disable scope leak enforcement. */
  protected void checkForLeakedScopes() throws IOException {
    checkForLeakedScopes.close();
  }

  // Assertions below here can eventually move to a new type

  /**
   * Ensures the inputs are parent and child, the parent starts before the child, and the duration
   * of the child is inside the parent's duration.
   */
  protected void assertSpanInInterval(MutableSpan span, long startTimestamp, long finishTimestamp) {
    assertThat(span.startTimestamp())
      .withFailMessage("Expected %s to start after %s", span, startTimestamp)
      .isGreaterThanOrEqualTo(startTimestamp);

    assertThat(span.finishTimestamp())
      .withFailMessage("Expected %s to finish after %s", span, finishTimestamp)
      .isLessThanOrEqualTo(finishTimestamp);
  }

  /** Ensures the first finished before the other started. */
  protected void assertSequential(MutableSpan span1, MutableSpan span2) {
    assertThat(span1.id())
      .withFailMessage("Expected different span IDs: %s %s", span1, span2)
      .isNotEqualTo(span2.id());

    assertThat(span1.finishTimestamp())
      .withFailMessage("Expected %s to finish before %s started", span1, span2)
      .isLessThanOrEqualTo(span2.startTimestamp());
  }

  /**
   * Useful for checking {@linkplain Span.Kind#SERVER server} spans when {@link
   * Tracing.Builder#supportsJoin(boolean)}.
   */
  protected void assertSameIds(MutableSpan span, TraceContext parent) {
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
    assertChildOf(new MutableSpan(child, null), parent);
  }

  protected void assertChildOf(MutableSpan child, TraceContext parent) {
    assertChildOf(child, new MutableSpan(parent, null));
  }

  protected void assertChildOf(MutableSpan child, MutableSpan parent) {
    assertThat(child.traceId())
      .withFailMessage("Expected to have trace ID(%s): %s", parent.traceId(), child)
      .isEqualTo(parent.traceId());

    assertThat(child.parentId())
      .withFailMessage("Expected to have parent ID(%s): %s", parent.id(), child)
      .isEqualTo(parent.id());
  }

  protected void assertNoError(MutableSpan result) {
    IntegrationTestSpanHandler.assertNoError(result);
  }

  protected void assertNoErrorTag(MutableSpan result) {
    IntegrationTestSpanHandler.assertNoErrorTag(result);
  }
}
