/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.context.jfr;

import brave.baggage.BaggageFields;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import brave.propagation.TraceContext;
import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;

/**
 * Adds {@linkplain Event} properties "traceId", "parentId" and "spanId" when a {@link
 * brave.Tracer#currentSpan() span is current}. These can be used to correlate JDK Flight recorder
 * events with logs or Zipkin.
 *
 * <p>Ex.
 * <pre>{@code
 * tracing = Tracing.newBuilder()
 *                  .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
 *                    .addScopeDecorator(JfrScopeDecorator.get())
 *                    .build()
 *                  )
 *                  ...
 *                  .build();
 * }</pre>
 */
public final class JfrScopeDecorator implements ScopeDecorator {
  static final ScopeDecorator INSTANCE = new JfrScopeDecorator();

  /**
   * Returns a singleton that configures {@link BaggageFields#TRACE_ID} and {@link
   * BaggageFields#SPAN_ID}.
   *
   * @since 5.11
   */
  public static ScopeDecorator get() {
    return INSTANCE;
  }

  @Category("Zipkin")
  @Label("Scope")
  @Description("Zipkin event representing a span being placed in scope")
  static final class ScopeEvent extends Event {
    @Label("Trace Id") String traceId;
    @Label("Parent Id") String parentId;
    @Label("Span Id") String spanId;
  }

  @Override public Scope decorateScope(@Nullable TraceContext context, Scope scope) {
    if (scope == Scope.NOOP) return scope; // we only scope fields constant in the context

    ScopeEvent event = new ScopeEvent();
    if (!event.isEnabled()) return scope;

    if (context != null) {
      event.traceId = context.traceIdString();
      event.parentId = context.parentIdString();
      event.spanId = context.spanIdString();
    }

    event.begin();

    class JfrCurrentTraceContextScope implements Scope {
      @Override public void close() {
        scope.close();
        event.commit();
      }
    }
    return new JfrCurrentTraceContextScope();
  }

  JfrScopeDecorator() {
  }
}
