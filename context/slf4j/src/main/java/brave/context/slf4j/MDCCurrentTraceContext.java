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

import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import org.slf4j.MDC;

/**
 * Adds {@linkplain MDC} properties "traceId", "parentId" and "spanId" when a {@link
 * brave.Tracer#currentSpan() span is current}. These can be used in log correlation.
 *
 * @deprecated use {@linkplain MDCScopeDecorator}. This will be removed in Brave v6.
 */
@Deprecated
public final class MDCCurrentTraceContext extends CurrentTraceContext {
  public static MDCCurrentTraceContext create() {
    return create(CurrentTraceContext.Default.inheritable());
  }

  public static MDCCurrentTraceContext create(CurrentTraceContext delegate) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    return new Builder(delegate).build();
  }

  static final class Builder extends CurrentTraceContext.Builder {
    final CurrentTraceContext delegate;

    Builder(CurrentTraceContext delegate) {
      this.delegate = delegate;
      addScopeDecorator(MDCScopeDecorator.create());
    }

    @Override public MDCCurrentTraceContext build() {
      return new MDCCurrentTraceContext(this);
    }
  }

  final CurrentTraceContext delegate;

  MDCCurrentTraceContext(Builder builder) {
    super(builder);
    delegate = builder.delegate;
  }

  @Override public TraceContext get() {
    return delegate.get();
  }

  @Override public Scope newScope(@Nullable TraceContext context) {
    return decorateScope(context, delegate.newScope(context));
  }

  @Override public Scope maybeScope(TraceContext context) {
    return decorateScope(context, delegate.maybeScope(context));
  }
}
