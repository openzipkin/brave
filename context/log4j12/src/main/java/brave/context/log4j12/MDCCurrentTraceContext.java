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
package brave.context.log4j12;

import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import org.apache.log4j.MDC;

/**
 * Adds {@linkplain MDC} properties "traceId", "parentId" and "spanId" when a {@link
 * brave.Tracer#currentSpan() span is current}. These can be used in log correlation.
 *
 * @deprecated use {@linkplain MDCScopeDecorator}. This will be removed in Brave v6.
 */
@Deprecated
public final class MDCCurrentTraceContext extends CurrentTraceContext {
  static final CurrentTraceContext.Builder SCOPE_DECORATING_BUILDER =
    ThreadLocalCurrentTraceContext.newBuilder().addScopeDecorator(MDCScopeDecorator.create());

  public static MDCCurrentTraceContext create() {
    return create(CurrentTraceContext.Default.inheritable());
  }

  public static MDCCurrentTraceContext create(CurrentTraceContext delegate) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    return new MDCCurrentTraceContext(delegate);
  }

  final CurrentTraceContext delegate;

  MDCCurrentTraceContext(CurrentTraceContext delegate) {
    super(SCOPE_DECORATING_BUILDER);
    this.delegate = delegate;
  }

  @Override public TraceContext get() {
    return delegate.get();
  }

  @Override public Scope newScope(@Nullable TraceContext currentSpan) {
    Scope scope = delegate.newScope(currentSpan);
    return decorateScope(currentSpan, scope);
  }
}
