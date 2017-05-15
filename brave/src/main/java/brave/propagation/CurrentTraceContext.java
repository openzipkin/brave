package brave.propagation;

import java.io.Closeable;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import javax.annotation.Nullable;

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
  /** Returns the current span in scope or null if there isn't one. */
  public abstract @Nullable TraceContext get();

  /**
   * Sets the current span in scope until the returned object is closed. It is a programming
   * error to drop or never close the result. Using try-with-resources is preferred for this reason.
   *
   * @param currentSpan span to place into scope or null to clear the scope
   */
  public abstract Scope newScope(@Nullable TraceContext currentSpan);

  /** A span remains in the scope it was bound to until close is called. */
  public interface Scope extends Closeable {
    /** No exceptions are thrown when unbinding a span scope. */
    @Override void close();
  }

  /** Default implementation which is backed by a static inheritable thread local */
  public static final class Default extends CurrentTraceContext {
    // static as we want one context per thread, not one context per thread-instance.
    // if this is not static, patterns that coordinate via statics (like Tracer.current()) will break.
    // static ThreadLocal was also used in Brave 3's ThreadLocalServerClientAndLocalSpanState
    static final InheritableThreadLocal<TraceContext> local = new InheritableThreadLocal<>();

    @Override public TraceContext get() {
      return local.get();
    }

    @Override public Scope newScope(TraceContext currentSpan) {
      final TraceContext previous = local.get();
      local.set(currentSpan);
      return () -> local.set(previous);
    }
  }

  /** Wraps the input so that it executes with the same context as now. */
  public <C> Callable<C> wrap(Callable<C> task) {
    final TraceContext invocationContext = get();
    return () -> {
      try (Scope scope = newScope(invocationContext)) {
        return task.call();
      }
    };
  }

  /** Wraps the input so that it executes with the same context as now. */
  public Runnable wrap(Runnable task) {
    final TraceContext invocationContext = get();
    return () -> {
      try (Scope scope = newScope(invocationContext)) {
        task.run();
      }
    };
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
