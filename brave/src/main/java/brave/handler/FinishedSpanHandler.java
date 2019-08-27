/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
import brave.Tracer;
import brave.internal.recorder.PendingSpans;
import brave.propagation.TraceContext;

/**
 * Triggered on each finished span except when spans that are {@link Span#isNoop() no-op}.
 *
 * <p>{@link TraceContext#sampled() Sampled spans} hit this stage before reporting to Zipkin.
 * This means changes to the mutable span will reflect in reported data.
 *
 * <p>When Zipkin's reporter is {@link zipkin2.reporter.Reporter#NOOP} or the context is
 * unsampled, this will still receive spans where {@link TraceContext#sampledLocal()} is true.
 *
 * @see #alwaysSampleLocal()
 */
public abstract class FinishedSpanHandler {
  /** Use to avoid comparing against null references */
  public static final FinishedSpanHandler NOOP = new FinishedSpanHandler() {
    @Override public boolean handle(TraceContext context, MutableSpan span) {
      return true;
    }

    @Override public String toString() {
      return "NoopFinishedSpanHandler{}";
    }
  };

  /**
   * This is invoked after a span is finished, allowing data to be modified or reported out of
   * process. A return value of false means the span should be dropped completely from the stream.
   *
   * <p>Changes to the input span are visible by later finished span handlers. One reason to change
   * the input is to align tags, so that correlation occurs. For example, some may clean the tag
   * "http.path" knowing downstream handlers such as zipkin reporting have the same value.
   *
   * <p>Returning false is the same effect as if {@link Span#abandon()} was called. Implementations
   * should be careful when returning false as it can lead to broken traces. Acceptable use cases
   * are when the span is a leaf, for example a client call to an uninstrumented database, or a
   * server call which is known to terminate in-process (for example, health-checks). Prefer an
   * instrumentation policy approach to this mechanism as it results in less overhead.
   *
   * <p>Implementations should not hold a reference to it after this method returns. This is to
   * allow object recycling.
   *
   * @param context the trace context which is {@link TraceContext#sampled()} or {@link
   * TraceContext#sampledLocal()}. This includes identifiers and potentially {@link
   * TraceContext#extra() extra propagated data} such as extended sampling configuration.
   * @param span a mutable object including all data recorded with span apis. Modifications are
   * visible to later handlers, including Zipkin.
   * @return true retains the span, and should almost always be used. false drops the span, making
   * it invisible to later handlers such as Zipkin.
   * @see #supportsOrphans() If you are scrubbing personal information, consider supporting orphans.
   */
  public abstract boolean handle(TraceContext context, MutableSpan span);

  /**
   * Normally, {@link #handle(TraceContext, MutableSpan)} is only called upon explicit termination
   * of a span: {@link Span#finish()}, {@link Span#finish(long)} or {@link Span#flush()}. When this
   * method returns true, the callback will also receive data orphaned due to spans being never
   * terminated or data added after termination. It is important to understand this, especially if
   * your handler is performing work like redaction. This sort of work needs to happen on all data,
   * not just the success paths.
   *
   * <h3>What is an orphaned span?</h3>
   *
   * <p>Brave adds an {@link Span#annotate(String) annotation} "brave.flush" when data remains
   * associated with a span when it is garbage collected. This is almost always a bug. For example,
   * calling {@link Span#tag(String, String)} after calling {@link Span#finish()}, or calling {@link
   * Tracer#nextSpan()} yet never using the result. To track down bugs like this, set the logger
   * {@link PendingSpans} to FINE level.
   *
   * <h3>Why handle orphaned spans?</h3>
   *
   * <p>Use cases for handling orphans include redaction, trimming the "brave.flush" annotation,
   * logging a different way than default, or incrementing bug counters. For example, you could use
   * the same credit card cleaner here as you do on the success path.
   *
   * <h3>What shouldn't handle orphaned spans?</h3>
   *
   * <p>As this is related to bugs, no assumptions can be made about span count etc. For example,
   * one span context can result in many calls to this handler, unrelated to the actual operation
   * performed. Handlers that redact or clean data work for normal spans and orphans. However,
   * aggregation handlers, such as dependency linkers or success/fail counters, can create problems
   * if used against orphaned spans.
   *
   * <h2>Implementation</h2>
   *
   * <p>By default, this method returns false, suggesting the implementation is not designed to
   * also process orphans. Return true to indicate otherwise. Whichever choice should be constant.
   * In other words do not sometimes return false and other times true, as the value is only read
   * once.
   *
   * <h3>Considerations for implementing {@code handle}</h3>
   *
   * <p>When this method returns true, the {@link #handle(TraceContext, MutableSpan) handle method}
   * is both invoked for normal spans and also orphaned ones. The following apply when handling
   * orphans:
   *
   * <p>The {@link TraceContext} parameter contains minimal information, including lookup ids
   * (traceId, spanId and localRootId) and sampling status. {@link TraceContext#extra() "extra"}
   * will be empty.
   *
   * <p>The {@link MutableSpan} parameter {@link MutableSpan#containsAnnotation(String) includes
   * the annotation} "brave.flush", and whatever state was orphaned (ex a tag).
   */
  public boolean supportsOrphans() {
    return false;
  }

  /**
   * When true, all spans become real spans even if they aren't sampled remotely. This allows
   * firehose instances (such as metrics) to consider attributes that are not always visible
   * before-the-fact, such as http paths. Defaults to false and affects {@link
   * TraceContext#sampledLocal()}.
   *
   * @see #handle(TraceContext, MutableSpan)
   */
  public boolean alwaysSampleLocal() {
    return false;
  }
}
