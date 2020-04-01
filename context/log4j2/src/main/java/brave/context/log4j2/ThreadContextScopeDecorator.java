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
package brave.context.log4j2;

import brave.internal.CorrelationContext;
import brave.propagation.CorrelationScopeDecorator;
import brave.propagation.CorrelationFields;
import brave.propagation.CurrentTraceContext;
import org.apache.logging.log4j.ThreadContext;

/**
 * Creates a {@link CorrelationScopeDecorator} for Log4j 2 {@linkplain ThreadContext Thread
 * Context}.
 *
 * <p>Ex.
 * <pre>{@code
 * tracing = Tracing.newBuilder()
 *                  .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
 *                    .addScopeDecorator(ThreadContextScopeDecorator.get())
 *                    .build()
 *                  )
 *                  ...
 *                  .build();
 * }</pre>
 *
 * @see CorrelationScopeDecorator
 */
public final class ThreadContextScopeDecorator {
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
      super(ThreadContextCorrelationContext.INSTANCE);
    }
  }

  // TODO: see if we can read/write directly to skip some overhead similar to
  // https://github.com/census-instrumentation/opencensus-java/blob/2903747aca08b1e2e29da35c5527ff046918e562/contrib/log_correlation/log4j2/src/main/java/io/opencensus/contrib/logcorrelation/log4j2/OpenCensusTraceContextDataInjector.java
  enum ThreadContextCorrelationContext implements CorrelationContext {
    INSTANCE;

    @Override public String get(String name) {
      return ThreadContext.get(name);
    }

    @Override public void put(String name, String value) {
      ThreadContext.put(name, value);
    }

    @Override public void remove(String name) {
      ThreadContext.remove(name);
    }
  }
}
