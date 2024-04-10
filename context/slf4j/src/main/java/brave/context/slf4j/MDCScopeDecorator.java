/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.context.slf4j;

import brave.baggage.BaggageFields;
import brave.baggage.CorrelationScopeDecorator;
import brave.internal.CorrelationContext;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import org.slf4j.MDC;

/**
 * Creates a {@link CorrelationScopeDecorator} for SLF4J {@linkplain MDC Mapped Diagnostic Context
 * (MDC)}.
 *
 * <p>Ex.
 * <pre>{@code
 * tracing = Tracing.newBuilder()
 *                  .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
 *                    .addScopeDecorator(MDCScopeDecorator.get())
 *                    .build()
 *                  )
 *                  ...
 *                  .build();
 * }</pre>
 *
 * @see CorrelationScopeDecorator
 * @since 5.2
 */
public final class MDCScopeDecorator {
  static final CurrentTraceContext.ScopeDecorator INSTANCE = new Builder().build();

  /**
   * Returns a singleton that configures {@link BaggageFields#TRACE_ID} and {@link
   * BaggageFields#SPAN_ID}.
   *
   * @since 5.11
   */
  public static CurrentTraceContext.ScopeDecorator get() {
    return INSTANCE;
  }

  /**
   * Returns a builder that configures {@link BaggageFields#TRACE_ID} and {@link
   * BaggageFields#SPAN_ID}.
   *
   * @since 5.11
   */
  public static CorrelationScopeDecorator.Builder newBuilder() {
    return new Builder();
  }

  static final class Builder extends CorrelationScopeDecorator.Builder {
    Builder() {
      super(MDCContext.INSTANCE);
    }
  }

  enum MDCContext implements CorrelationContext {
    INSTANCE;

    @Override public String getValue(String name) {
      return MDC.get(name);
    }

    @Override public boolean update(String name, @Nullable String value) {
      if (value != null) {
        MDC.put(name, value);
      } else {
        MDC.remove(name);
      }
      return true;
    }
  }
}
