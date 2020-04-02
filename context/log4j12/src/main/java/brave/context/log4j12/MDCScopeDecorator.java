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
package brave.context.log4j12;

import brave.internal.CorrelationContext;
import brave.internal.Nullable;
import brave.baggage.BaggageFields;
import brave.baggage.CorrelationScopeDecorator;
import brave.propagation.CurrentTraceContext;
import org.apache.log4j.MDC;

/**
 * Creates a {@link CorrelationScopeDecorator} for Log4j 1.2 {@linkplain MDC Mapped Diagnostic
 * Context (MDC)}.
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

  /**
   * Returns a scope decorator that configures {@link BaggageFields#TRACE_ID}, {@link
   * BaggageFields#PARENT_ID}, {@link BaggageFields#SPAN_ID} and {@link BaggageFields#SAMPLED}
   *
   * @since 5.2
   * @deprecated since 5.11 use {@link #get()} or {@link #newBuilder()}
   */
  @Deprecated public static CurrentTraceContext.ScopeDecorator create() {
    return new Builder()
      .clearFields()
      .addField(BaggageFields.TRACE_ID)
      .addField(BaggageFields.PARENT_ID)
      .addField(BaggageFields.SPAN_ID)
      .addField(BaggageFields.SAMPLED)
      .build();
  }

  static final class Builder extends CorrelationScopeDecorator.Builder {
    Builder() {
      super(MDCContext.INSTANCE);
    }
  }

  enum MDCContext implements CorrelationContext {
    INSTANCE;

    @Override public String getValue(String name) {
      Object result = MDC.get(name);
      return result instanceof String ? (String) result : null;
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
