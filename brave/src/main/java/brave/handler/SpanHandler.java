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
package brave.handler;

import brave.Span;
import brave.Tracing;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import java.lang.ref.WeakReference;

/**
 * This tracks one recording of a {@link TraceContext}. Common implementations include span
 * reporting (ex to Zipkin) and data manipulation, such as redaction for security purposes.
 *
 * <h3>Relationship to Span lifecycle</h3>
 * The pair of {@link #begin} and {@link #end} seems the same as the span lifecycle. In most cases
 * it will be the same, but you cannot assume this.
 *
 * <p>A {@link TraceContext} could be recorded twice, for example, if a long operation
 * began, called {@link Span#flush()} (recording 1) and later called {@link Span#finish()}
 * (recording 2). A {@link TraceContext} could be abrupted by garbage collection resulting in a
 * {@link Cause#ABANDON}. A user could even {@linkplain Cause#ABANDON abandon} a span without
 * recording anything!
 *
 * <p>Collectors that process finished spans will need to look at the {link Cause} and {@link
 * MutableSpan} collected. For example, {@link Cause#FINISH} is usually a good enough heuristic to
 * find complete spans.
 *
 * <h3>Advanced Notes</h3>
 * <p>It is important to do work quickly as callbacks are run on the same thread as application
 * code. However, do not mutate {@link MutableSpan} between callbacks, as it is not thread safe.
 *
 * <p>The {@link TraceContext} and {@link MutableSpan} parameter from {@link #begin} will be
 * the same reference for {@link #end}.
 *
 * <p>If caching the context or span parameters between callbacks, consider a {@link WeakReference}
 * to avoid holding up garbage collection.
 *
 * <p>The {@link #begin} callback primarily supports tracking of children, or partitioning of
 * data for backend that needs to see an entire {@linkplain TraceContext#localRootId() local root}.
 */
public abstract class SpanHandler {
  /** What ended the data collection? */
  public enum Cause {
    /**
     * Called on {@link Span#abandon()}.
     *
     * <p>This is useful when counting children. Decrement your counter when this occurs as the
     * span will not be reported.
     *
     * <p><em>Note:</em>Abandoned spans should be ignored as they aren't indicative of an error.
     * Some instrumentation speculatively create a span for possible outcomes such as retry.
     */
    ABANDON,
    /**
     * Called on {@link Span#finish()} and is the simplest cause to reason with. When {@link
     * MutableSpan#startTimestamp()} is present, you can assume with high confidence you have all
     * recorded data for this span.
     */
    FINISH,
    /**
     * Called on {@link Span#flush()}.
     *
     * <p>Even though the span here is incomplete (missing {@link MutableSpan#finishTimestamp()},
     * it is reported to the tracing system unless a {@link FinishedSpanHandler} returns false.
     */
    FLUSH,
    /**
     * Called when the trace context was garbage collected prior to completion.
     *
     * <p>Unlike {@link FinishedSpanHandler#supportsOrphans()}, this is called even if empty.
     * Non-empty spans are reported to the tracing system unless a {@link FinishedSpanHandler}
     * returns false.
     *
     * @see FinishedSpanHandler#supportsOrphans()
     */
    ORPHAN
  }

  /** Use to avoid comparing against null references */
  public static final SpanHandler NOOP = new SpanHandler() {
    @Override public String toString() {
      return "NoopSpanHandler{}";
    }
  };

  /**
   * This is called when a span is sampled, but before it is started.
   *
   * @param context the trace context which is  {@link TraceContext#sampledLocal()}. This includes
   * identifiers and potentially {@link TraceContext#extra() extra propagated data} such as baggage
   * or extended sampling configuration.
   * @param span a mutable object that stores data recorded with span apis. Modifications are
   * visible to later collectors.
   * @param parent can be {@code null} only when the new context is a {@linkplain
   * TraceContext#isLocalRoot() local root}.
   * @return {@code true} retains the span, and should almost always be used. {@code false} makes it
   * invisible to later handlers such as Zipkin.
   * @see Tracing.Builder#alwaysSampleLocal()
   */
  public boolean begin(TraceContext context, MutableSpan span, @Nullable TraceContext parent) {
    return true;
  }

  /**
   * Called when data collection completes for one of the following reasons:
   *
   * <p><ol>
   * <li>{@link Cause#ABANDON} if it was a speculative context</li>
   * <li>{@link Cause#FINISH} if it was reported complete</li>
   * <li>{@link Cause#FLUSH} if it was intentionally reported incomplete</li>
   * <li>{@link Cause#ORPHAN} if it was reported incomplete due to garbage collection</li>
   * </ol>
   *
   * @param context same instance as passed to {@link #begin}
   * @param span same instance as passed to {@link #begin}
   * @param cause why the data collection stopped.
   * @return {@code true} retains the span, and should almost always be used. {@code false} drops
   * the span, making it invisible to later handlers such as Zipkin.
   */
  public boolean end(TraceContext context, MutableSpan span, Cause cause) {
    return true;
  }
}
