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

import brave.internal.propagation.CorrelationFieldScopeDecoratorBuilder;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import org.slf4j.MDC;

/**
 * Adds {@linkplain MDC} properties "traceId", "parentId", "spanId" and "sampled" when a {@link
 * brave.Tracer#currentSpan() span is current}. "traceId" and "spanId" are used in log correlation.
 * "parentId" is used for scenarios such as log parsing that reconstructs the trace tree. "sampled"
 * is used as a hint that a span found in logs might be in Zipkin.
 *
 * <p>Ex.
 * <pre>{@code
 * tracing = Tracing.newBuilder()
 *                  .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
 *                    .addScopeDecorator(MDCScopeDecorator.create())
 *                    .build()
 *                  )
 *                  ...
 *                  .build();
 * }</pre>
 */
public final class MDCScopeDecorator {
  /** @since 5.11 */
  public static Builder newBuilder() {
    return new Builder();
  }

  public static ScopeDecorator create() {
    return new Builder().build();
  }

  /** @since 5.11 */
  public static final class Builder extends CorrelationFieldScopeDecoratorBuilder<Builder> {
    /** {@inheritDoc} */
    @Override public Builder removeField(String fieldName) {
      return super.removeField(fieldName);
    }

    /** {@inheritDoc} */
    @Override public Builder addExtraField(String fieldName) {
      return super.addExtraField(fieldName);
    }

    enum MDCContext implements Context {
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

    Builder() {
      super(MDCContext.INSTANCE);
    }
  }
}
