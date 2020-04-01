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
package brave.context.slf4j;

import brave.internal.CorrelationContext;
import brave.propagation.CorrelationScopeDecorator;
import brave.propagation.CorrelationFields;
import brave.propagation.CurrentTraceContext;
import org.slf4j.MDC;

/**
 * Creates a {@link CorrelationScopeDecorator} for SLF4J {@linkplain MDC Mapped Diagnostic
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
 * @since 5.2
 */
public final class MDCScopeDecorator {
  static final CurrentTraceContext.ScopeDecorator INSTANCE = new Builder().build();

  /**
   * Returns a singleton that configures {@link CorrelationFields#TRACE_ID} and {@link
   * CorrelationFields#SPAN_ID}.
   *
   * @since 5.11
   */
  public static CurrentTraceContext.ScopeDecorator get() {
    return INSTANCE;
  }

  /**
   * Returns a builder that configures {@link CorrelationFields#TRACE_ID} and {@link
   * CorrelationFields#SPAN_ID}.
   *
   * @since 5.11
   */
  public static CorrelationScopeDecorator.Builder newBuilder() {
    return new Builder();
  }

  /**
   * Returns a scope decorator that configures {@link CorrelationFields#TRACE_ID}, {@link
   * CorrelationFields#PARENT_ID}, {@link CorrelationFields#SPAN_ID} and {@link
   * CorrelationFields#SAMPLED}
   *
   * @since 5.2
   * @deprecated since 5.11 use {@link #get()} or {@link #newBuilder()}
   */
  @Deprecated public static CurrentTraceContext.ScopeDecorator create() {
    return new Builder()
      .clearFields()
      .addField(CorrelationFields.TRACE_ID)
      .addField(CorrelationFields.PARENT_ID)
      .addField(CorrelationFields.SPAN_ID)
      .addField(CorrelationFields.SAMPLED)
      .build();
  }

  static final class Builder extends CorrelationScopeDecorator.Builder {
    Builder() {
      super(MDCContext.INSTANCE);
    }
  }

  enum MDCContext implements CorrelationContext {
    INSTANCE;

    @Override public String get(String name) {
      return MDC.get(name);
    }

    @Override public void put(String name, String value) {
      MDC.put(name, value);
    }

    @Override public void remove(String name) {
      MDC.remove(name);
    }
  }
}
