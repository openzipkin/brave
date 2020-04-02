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
package brave.propagation;

import brave.internal.Nullable;
import java.io.Closeable;

/**
 * Useful when developing instrumentation as state is enforced more strictly.
 *
 * <p>For example, it is instance scoped as opposed to static scoped, not inheritable and throws an
 * exception if a scope is closed on a different thread that it was opened on.
 *
 * @see StrictScopeDecorator
 */
public final class StrictCurrentTraceContext extends CurrentTraceContext implements Closeable {
  /** @since 5.11 */
  public static StrictCurrentTraceContext create() {
    return new StrictCurrentTraceContext();
  }

  /** @since 5.11 */
  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder extends CurrentTraceContext.Builder {
    // intentionally not inheritable to ensure instrumentation propagation doesn't accidentally work
    // intentionally not static to make explicit when instrumentation need per thread semantics
    final ThreadLocal<TraceContext> local = new ThreadLocal<>();
    CurrentTraceContext delegate = new ThreadLocalCurrentTraceContext.Builder(local).build();
    StrictScopeDecorator strictScopeDecorator = new StrictScopeDecorator();

    @Override public StrictCurrentTraceContext build() {
      delegate = new ThreadLocalCurrentTraceContext.Builder(local)
        .addScopeDecorator(strictScopeDecorator)
        .build();
      return new StrictCurrentTraceContext(this);
    }

    @Override public Builder addScopeDecorator(ScopeDecorator scopeDecorator) {
      if (scopeDecorator instanceof StrictScopeDecorator) {
        strictScopeDecorator = (StrictScopeDecorator) scopeDecorator;
        return this;
      }
      return (Builder) super.addScopeDecorator(scopeDecorator);
    }

    Builder() {
    }
  }

  final CurrentTraceContext delegate;
  final StrictScopeDecorator strictScopeDecorator;

  public StrictCurrentTraceContext() { // Preserve historical public ctor
    this(new Builder());
  }

  StrictCurrentTraceContext(Builder builder) {
    super(builder);
    delegate = builder.delegate;
    strictScopeDecorator = builder.strictScopeDecorator;
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

  /**
   * Calls {@link StrictScopeDecorator#close()}
   *
   * @since 5.11
   */
  @Override public void close() {
    strictScopeDecorator.close();
  }
}
