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
package brave.propagation;

import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.CurrentTraceContext.ScopeDecorator;

/**
 * Useful when developing instrumentation as state is enforced more strictly.
 *
 * <p>Ex.
 * <pre>{@code
 * tracing = Tracing.newBuilder()
 *                  .spanReporter(...)
 *                  .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
 *                    .addScopeDecorator(StrictScopeDecorator.create())
 *                    .build()
 *                  ).build();
 * }</pre>
 */
public final class StrictScopeDecorator implements ScopeDecorator {
  public static ScopeDecorator create() {
    return new StrictScopeDecorator();
  }

  /** Identifies problems by throwing assertion errors when a scope is closed on a different thread. */
  @Override public Scope decorateScope(@Nullable TraceContext currentSpan, Scope scope) {
    return new StrictScope(scope, new Error(String.format("Thread %s opened scope for %s here:",
      Thread.currentThread().getName(), currentSpan)));
  }

  static final class StrictScope implements Scope {
    final Scope delegate;
    final Throwable caller;
    final long threadId = Thread.currentThread().getId();

    StrictScope(Scope delegate, Throwable caller) {
      this.delegate = delegate;
      this.caller = caller;
    }

    @Override public void close() {
      if (Thread.currentThread().getId() != threadId) {
        throw new IllegalStateException(
          "scope closed in a different thread: " + Thread.currentThread().getName(),
          caller);
      }
      delegate.close();
    }

    @Override public String toString() {
      return caller.toString();
    }
  }

  StrictScopeDecorator() {
  }
}
