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

import brave.Tracer;
import brave.Tracing;
import brave.internal.Nullable;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * This makes a given span the current span by placing it in scope (usually but not always a thread
 * local scope).
 *
 * <p>This type is an SPI, and intended to be used by implementors looking to change thread-local
 * storage, or integrate with other contexts such as logging (MDC).
 *
 * <h3>Design</h3>
 *
 * This design was inspired by com.google.instrumentation.trace.ContextUtils,
 * com.google.inject.servlet.RequestScoper and com.github.kristofa.brave.CurrentSpan
 */
public abstract class CurrentTraceContext {
  static {
    // ensure a reference to InternalPropagation exists
    SamplingFlags.DEBUG.toString();
  }

  /** Implementations of this allow standardized configuration, for example scope decoration. */
  public abstract static class Builder {
    ArrayList<ScopeDecorator> scopeDecorators = new ArrayList<>();

    /** Implementations call decorators in order to add features like log correlation to a scope. */
    public Builder addScopeDecorator(ScopeDecorator scopeDecorator) {
      if (scopeDecorator == null) throw new NullPointerException("scopeDecorator == null");
      this.scopeDecorators.add(scopeDecorator);
      return this;
    }

    public abstract CurrentTraceContext build();
  }

  /** Returns the current span in scope or null if there isn't one. */
  public abstract @Nullable TraceContext get();

  /**
   * Sets the current span in scope until the returned object is closed. It is a programming error
   * to drop or never close the result. Using try-with-resources is preferred for this reason.
   *
   * @param currentSpan span to place into scope or null to clear the scope
   */
  public abstract Scope newScope(@Nullable TraceContext currentSpan);

  final List<ScopeDecorator> scopeDecorators;

  protected CurrentTraceContext() {
    this.scopeDecorators = Collections.emptyList();
  }

  protected CurrentTraceContext(Builder builder) {
    this.scopeDecorators = new ArrayList<>(builder.scopeDecorators);
  }

  /**
   * When implementing {@linkplain #newScope(TraceContext)}, decorate the result before returning
   * it.
   *
   * <p>Ex.
   * <pre>{@code
   *   @Override public Scope newScope(@Nullable TraceContext currentSpan) {
   *     final TraceContext previous = local.get();
   *     local.set(currentSpan);
   *     class ThreadLocalScope implements Scope {
   *       @Override public void close() {
   *         local.set(previous);
   *       }
   *     }
   *     Scope result = new ThreadLocalScope();
   *     // ensure scope hooks are attached to the result
   *     return decorateScope(currentSpan, result);
   *   }
   * }</pre>
   */
  protected Scope decorateScope(@Nullable TraceContext currentSpan, Scope scope) {
    int length = scopeDecorators.size();
    for (int i = 0; i < length; i++) {
      scope = scopeDecorators.get(i).decorateScope(currentSpan, scope);
    }
    return scope;
  }

  /**
   * Like {@link #newScope(TraceContext)}, except returns {@link Scope#NOOP} if the given context is
   * already in scope. This can reduce overhead when scoping callbacks. However, this will not apply
   * any changes, notably in {@link TraceContext#extra()}. As such, it should be used carefully and
   * only in conditions where redundancy is possible and the intent is primarily to facilitate
   * {@link Tracer#currentSpan}. Most often, this is used to eliminate redundant scopes by
   * wrappers.
   *
   * <p>For example, RxJava includes hooks to wrap types that represent an asynchronous functional
   * composition. For example, {@code flowable.parallel().flatMap(Y).sequential()} Assembly hooks
   * can ensure each stage of this operation can see the initial trace context. However, other tools
   * can also instrument the stages, including vert.x or even agent instrumentation. When wrapping
   * callbacks, it can reduce overhead to use {@code maybeScope} as opposed to {@code newScope}.
   *
   * <p>Generally speaking, this is best used for wrappers, such as executor services or lifecycle
   * hooks, which usually have no current trace context when invoked.
   *
   * <h3>Implementors note</h3>
   * <p>For those overriding this method, you must compare {@link TraceContext#traceIdHigh()},
   * {@link TraceContext#traceId()} and {@link TraceContext#spanId()} to decide if the contexts are
   * equivalent. Due to details of propagation, other data like parent ID are not considered in
   * equivalence checks.
   *
   * @param currentSpan span to place into scope or null to clear the scope
   * @return a new scope object or {@link Scope#NOOP} if the input is already the case
   */
  public Scope maybeScope(@Nullable TraceContext currentSpan) {
    TraceContext currentScope = get();
    if (currentSpan == null) {
      if (currentScope == null) return Scope.NOOP;
      return newScope(null);
    }
    return currentSpan.equals(currentScope) ? Scope.NOOP : newScope(currentSpan);
  }

  /** A span remains in the scope it was bound to until close is called. */
  public interface Scope extends Closeable {
    /**
     * Returned when {@link CurrentTraceContext#maybeScope(TraceContext)} detected scope
     * redundancy.
     */
    Scope NOOP = new Scope() {
      @Override public void close() {
      }

      @Override public String toString() {
        return "NoopScope";
      }
    };

    /** No exceptions are thrown when unbinding a span scope. */
    @Override void close();
  }

  /**
   * Use this to add features such as thread checks or log correlation fields when a scope is
   * created or closed.
   */
  public interface ScopeDecorator {
    Scope decorateScope(@Nullable TraceContext currentSpan, Scope scope);
  }

  /**
   * Default implementation which is backed by a static thread local.
   *
   * <p>A static thread local ensures we have one context per thread, as opposed to one per thread-
   * tracer. This means all tracer instances will be able to see any tracer's contexts.
   *
   * <p>The trade-off of this (instance-based reference) vs the reverse: trace contexts are not
   * separated by tracer by default. For example, to make a trace invisible to another tracer, you
   * have to use a non-default implementation.
   *
   * <p>Sometimes people make different instances of the tracer just to change configuration like
   * the local service name. If we used a thread-instance approach, none of these would be able to
   * see eachother's scopes. This would break {@link Tracing#currentTracer()} scope visibility in a
   * way few would want to debug. It might be phrased as "MySQL always starts a new trace and I
   * don't know why."
   *
   * <p>If you want a different behavior, use a different subtype of {@link CurrentTraceContext},
   * possibly your own, or raise an issue and explain what your use case is.
   */
  public static final class Default extends ThreadLocalCurrentTraceContext {
    // Inheritable as Brave 3's ThreadLocalServerClientAndLocalSpanState was inheritable
    static final InheritableThreadLocal<TraceContext> INHERITABLE = new InheritableThreadLocal<>();

    /** Uses a non-inheritable static thread local */
    public static CurrentTraceContext create() {
      return new ThreadLocalCurrentTraceContext(new Builder(), DEFAULT);
    }

    /**
     * Uses an inheritable static thread local which allows arbitrary calls to {@link
     * Thread#start()} to automatically inherit this context. This feature is available as it is was
     * the default in Brave 3, because some users couldn't control threads in their applications.
     *
     * <p>This can be a problem in scenarios such as thread pool expansion, leading to data being
     * recorded in the wrong span, or spans with the wrong parent. If you are impacted by this,
     * switch to {@link #create()}.
     */
    public static CurrentTraceContext inheritable() {
      return new Default();
    }

    Default() {
      super(new Builder(), INHERITABLE);
    }
  }

  /** Wraps the input so that it executes with the same context as now. */
  public <C> Callable<C> wrap(Callable<C> task) {
    final TraceContext invocationContext = get();
    class CurrentTraceContextCallable implements Callable<C> {
      @Override public C call() throws Exception {
        try (Scope scope = maybeScope(invocationContext)) {
          return task.call();
        }
      }
    }
    return new CurrentTraceContextCallable();
  }

  /** Wraps the input so that it executes with the same context as now. */
  // TODO: here and elsewhere consider a volatile reference. When the invocation context equals an
  // existing wrapped context, it isn't necessarily the same as fields in context.extra may be
  // different and equals does not consider extra. For example, if a new propagation field has been
  // added, this should be considered. Doing so via a reference swap could be a lot cheaper than
  // re-wrapping and achieve the same goal.
  public Runnable wrap(Runnable task) {
    final TraceContext invocationContext = get();
    class CurrentTraceContextRunnable implements Runnable {
      @Override public void run() {
        try (Scope scope = maybeScope(invocationContext)) {
          task.run();
        }
      }
    }
    return new CurrentTraceContextRunnable();
  }

  /**
   * Decorates the input such that the {@link #get() current trace context} at the time a task is
   * scheduled is made current when the task is executed.
   */
  public Executor executor(Executor delegate) {
    class CurrentTraceContextExecutor implements Executor {
      @Override public void execute(Runnable task) {
        delegate.execute(CurrentTraceContext.this.wrap(task));
      }
    }
    return new CurrentTraceContextExecutor();
  }

  /**
   * Decorates the input such that the {@link #get() current trace context} at the time a task is
   * scheduled is made current when the task is executed.
   */
  public ExecutorService executorService(ExecutorService delegate) {
    class CurrentTraceContextExecutorService extends brave.internal.WrappingExecutorService {

      @Override protected ExecutorService delegate() {
        return delegate;
      }

      @Override protected <C> Callable<C> wrap(Callable<C> task) {
        return CurrentTraceContext.this.wrap(task);
      }

      @Override protected Runnable wrap(Runnable task) {
        return CurrentTraceContext.this.wrap(task);
      }
    }
    return new CurrentTraceContextExecutorService();
  }
}
