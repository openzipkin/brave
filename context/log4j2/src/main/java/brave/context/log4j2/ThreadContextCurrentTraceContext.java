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

import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import org.apache.logging.log4j.ThreadContext;

/**
 * Adds {@linkplain ThreadContext} properties "traceId", "parentId" and "spanId" when a {@link
 * brave.Tracer#currentSpan() span is current}. These can be used in log correlation.
 *
 * @deprecated use {@linkplain ThreadContextScopeDecorator}. This will be removed in Brave v6.
 */
@Deprecated
public final class ThreadContextCurrentTraceContext extends CurrentTraceContext {
  public static ThreadContextCurrentTraceContext create() {
    return create(CurrentTraceContext.Default.inheritable());
  }

  public static ThreadContextCurrentTraceContext create(CurrentTraceContext delegate) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    return new Builder(delegate).build();
  }

  static final class Builder extends CurrentTraceContext.Builder {
    final CurrentTraceContext delegate;

    Builder(CurrentTraceContext delegate) {
      this.delegate = delegate;
      addScopeDecorator(ThreadContextScopeDecorator.create());
    }

    @Override public ThreadContextCurrentTraceContext build() {
      return new ThreadContextCurrentTraceContext(this);
    }
  }

  final CurrentTraceContext delegate;

  ThreadContextCurrentTraceContext(Builder builder) {
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
