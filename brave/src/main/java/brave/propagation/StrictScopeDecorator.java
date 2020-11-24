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

import brave.Tracer;
import brave.internal.Nullable;
import brave.internal.collect.WeakConcurrentMap;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import java.io.Closeable;
import java.lang.ref.Reference;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Executor;

import static java.lang.Thread.currentThread;

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
// Closeable so things like Spring will automatically execute it on shutdown and expose leaks!
public final class StrictScopeDecorator implements ScopeDecorator, Closeable {
  public static StrictScopeDecorator create() {
    return new StrictScopeDecorator();
  }

  final PendingScopes pendingScopes = new PendingScopes();

  /**
   * Identifies problems by throwing {@link IllegalStateException} when a scope is closed on a
   * different thread.
   */
  @Override public Scope decorateScope(@Nullable TraceContext context, Scope scope) {
    if (scope == Scope.NOOP) return scope; // don't track no-op scopes as they cannot leak

    CallerStackTrace caller = new CallerStackTrace(context);
    StackTraceElement[] stackTrace = caller.getStackTrace();

    // "new CallerStackTrace(context)" isn't the line we want to start the caller stack trace with
    int i = 1;

    // This skips internal utilities in this jar. Notably, this will not skip utilities outside it.
    // For example, HTTP or messaging handlers will become the caller, as would wrappers over Brave,
    // such as brave-opentracing. This is ok, as if they have bugs, they will show up as the caller!
    while (i < stackTrace.length) {
      String className = stackTrace[i].getClassName();
      if (className.equals(Tracer.class.getName())
        || className.endsWith("CurrentTraceContext") // subtypes with conventional names
        || className.equals(ThreadLocalSpan.class.getName())) {
        i++;
      } else {
        break;
      }
    }
    int from = i;

    stackTrace = Arrays.copyOfRange(stackTrace, from, stackTrace.length);
    caller.setStackTrace(stackTrace);

    Scope strictScope = new StrictScope(scope, caller);
    pendingScopes.putIfProbablyAbsent(strictScope, caller);
    return strictScope;
  }

  /**
   * This is useful in tests to help ensure scopes are not leaked by instrumentation.
   *
   * <p><em>Note:</em> It is important to close all resources prior to calling this, so that
   * in-flight operations are not mistaken as scope leaks. If this raises an error, consider if a
   * {@linkplain CurrentTraceContext#executor(Executor) wrapped executor} is still running.
   *
   * @throws AssertionError if any scopes were left unclosed.
   * @since 5.11
   */
  // AssertionError to ensure test runners render the stack trace
  @Override public void close() {
    // toArray is synchronized while iterators are not
    pendingScopes.expungeStaleEntries();

    for (Map.Entry<Scope, CallerStackTrace> entry : pendingScopes) {
      CallerStackTrace caller = entry.getValue();
      if (!caller.closed) {
        throwCallerError(caller);
      }
    }
  }

  static void throwCallerError(CallerStackTrace caller) {
    // Sometimes unit test runners truncate the cause of the exception.
    // This flattens the exception as the caller of close() isn't important vs the one that leaked
    AssertionError toThrow = new AssertionError(
      "Thread [" + caller.threadName + "] opened a scope of " + caller.context + " here:");
    toThrow.setStackTrace(caller.getStackTrace());
    throw toThrow;
  }

  final class StrictScope implements Scope {
    final Scope delegate;
    final CallerStackTrace caller;

    StrictScope(Scope delegate, CallerStackTrace caller) {
      this.delegate = delegate;
      this.caller = caller;
    }

    @Override public void close() {
      caller.closed = true;

      pendingScopes.remove(this);

      if (currentThread().getId() != caller.threadId) {
        throw new IllegalStateException(String.format(
          "Thread [%s] opened scope, but thread [%s] closed it", caller.threadName,
          currentThread().getName()), caller);
      }
      delegate.close();
    }

    @Override public String toString() {
      return caller.getMessage();
    }
  }

  static class CallerStackTrace extends Throwable {
    final String threadName = currentThread().getName();
    final long threadId = currentThread().getId();
    final TraceContext context;

    volatile boolean closed;

    CallerStackTrace(@Nullable TraceContext context) {
      super("Thread [" + currentThread().getName() + "] opened scope for " + context + " here:");
      this.context = context;
    }
  }

  static class PendingScopes extends WeakConcurrentMap<Scope, CallerStackTrace> {
    @Override
    protected void expungeStaleEntries() {
      Reference<?> reference;
      while ((reference = poll()) != null) {
        CallerStackTrace caller = removeStaleEntry(reference);
        if (caller == null || caller.closed) continue;
        throwCallerError(caller);
      }
    }
  }

  StrictScopeDecorator() {
  }
}
