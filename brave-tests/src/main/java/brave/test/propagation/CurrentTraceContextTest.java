package brave.test.propagation;

import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import brave.test.util.ClassLoaders;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadableWithSupplier;
import static brave.test.util.ClassLoaders.newInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

public abstract class CurrentTraceContextTest {

  protected final CurrentTraceContext currentTraceContext;
  protected final TraceContext context =
      TraceContext.newBuilder().traceIdHigh(-1L).traceId(1L).spanId(1L).build();
  protected final TraceContext context2 =
      context.toBuilder().parentId(context.spanId()).spanId(-2L).build();

  protected abstract Class<? extends Supplier<CurrentTraceContext>> currentSupplier();

  protected CurrentTraceContextTest() {
    currentTraceContext = newInstance(currentSupplier(), getClass().getClassLoader()).get();
  }

  protected void verifyImplicitContext(@Nullable TraceContext context) {
  }

  @Test public void currentSpan_defaultsToNull() {
    assertThat(currentTraceContext.get()).isNull();
  }

  @Test public void newScope_retainsContext() {
    retainsContext(currentTraceContext.newScope(context));
  }

  @Test public void maybeScope_retainsContext() {
    retainsContext(currentTraceContext.maybeScope(context));
  }

  void retainsContext(Scope scope) {
    try {
      assertThat(scope).isNotEqualTo(Scope.NOOP);
      assertThat(currentTraceContext.get())
          .isEqualTo(context);
      verifyImplicitContext(context);
    } finally {
      scope.close();
    }
  }

  @Test public void newScope_noticesDifferentSpanId() {
    noticesDifferentSpanId(currentTraceContext.newScope(context));
  }

  @Test public void maybeScope_noticesDifferentSpanId() {
    noticesDifferentSpanId(currentTraceContext.maybeScope(context));
  }

  void noticesDifferentSpanId(Scope scope) {
    TraceContext differentSpanId = context.toBuilder().spanId(context.spanId() + 1L).build();
    try (Scope scope2 = currentTraceContext.maybeScope(differentSpanId)) {
      assertThat(scope2).isNotEqualTo(Scope.NOOP);
      assertThat(currentTraceContext.get())
          .isEqualTo(differentSpanId);
      verifyImplicitContext(differentSpanId);
    } finally {
      scope.close();
    }
  }

  @Test public void newScope_noticesDifferentContext() {
    noticesDifferentContext(currentTraceContext.newScope(context));
  }

  @Test public void maybeScope_noticesDifferentContext() {
    noticesDifferentContext(currentTraceContext.maybeScope(context));
  }

  void noticesDifferentContext(Scope scope) {
    try (Scope scope2 = currentTraceContext.maybeScope(context2)) {
      assertThat(scope2).isNotEqualTo(Scope.NOOP);
      assertThat(currentTraceContext.get())
          .isEqualTo(context2);
      verifyImplicitContext(context2);
    } finally {
      scope.close();
    }
  }

  @Test public void maybeScope_doesntDuplicateContext() {
    try (Scope scope = currentTraceContext.newScope(context)) {
      try (Scope scope2 = currentTraceContext.maybeScope(context)) {
        assertThat(scope2).isEqualTo(Scope.NOOP);
      }
    }
  }

  @Test public void newScope_canClearScope() {
    canClearScope(() -> currentTraceContext.newScope(null));
  }

  @Test public void maybeScope_canClearScope() {
    canClearScope(() -> currentTraceContext.maybeScope(null));
  }

  @Test public void maybeScope_doesntDuplicateContext_onNull() {
    try (Scope scope2 = currentTraceContext.maybeScope(null)) {
      assertThat(scope2).isEqualTo(Scope.NOOP);
    }
  }

  void canClearScope(Supplier<Scope> noScoper) {
    try (Scope scope = currentTraceContext.newScope(context)) {
      try (Scope noScope = noScoper.get()) {
        assertThat(noScope).isNotEqualTo(Scope.NOOP);
        assertThat(currentTraceContext.get())
            .isNull();
        verifyImplicitContext(null);
      }

      // old context reverted
      assertThat(currentTraceContext.get())
          .isEqualTo(context);
      verifyImplicitContext(context);
    }
  }

  protected void is_inheritable(CurrentTraceContext inheritableCurrentTraceContext)
      throws Exception {
    // use a single-threaded version of newCachedThreadPool
    ExecutorService service = new ThreadPoolExecutor(0, 1,
        60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    // submitting a job grows the pool, attaching the context to its thread
    try (Scope scope = inheritableCurrentTraceContext.newScope(context)) {
      assertThat(service.submit(inheritableCurrentTraceContext::get).get())
          .isEqualTo(context);
    }

    // same thread executes the next job and still has the same context (leaked and not cleaned up)
    assertThat(service.submit(inheritableCurrentTraceContext::get).get())
        .isEqualTo(context);

    service.shutdownNow();
  }

  @Test public void isnt_inheritable() throws Exception {
    ExecutorService service = Executors.newCachedThreadPool();

    try (Scope scope = currentTraceContext.newScope(context)) {
      assertThat(service.submit(() -> {
        verifyImplicitContext(null);
        return currentTraceContext.get();
      }).get()).isNull();
    } catch (ExecutionException e) {
      if (e.getCause() instanceof Error) throw (Error) e.getCause();
      throw (Exception) e.getCause();
    }

    assertThat(service.submit(currentTraceContext::get).get())
        .isNull();
    verifyImplicitContext(null);

    service.shutdownNow();
  }

  @Test public void attachesSpanInCallable_canClear() throws Exception {
    Callable<?> callable = currentTraceContext.wrap(() -> {
      assertThat(currentTraceContext.get()).isNull();
      verifyImplicitContext(null);
      return true;
    });

    // Set another span between the time the task was made and executed.
    try (Scope scope2 = currentTraceContext.newScope(context2)) {
      callable.call(); // runs assertion
      verifyImplicitContext(context2);
    }
  }

  @Test public void attachesSpanInCallable() throws Exception {
    Callable<?> callable;
    try (Scope scope = currentTraceContext.newScope(context)) {
      callable = currentTraceContext.wrap(() -> {
        assertThat(currentTraceContext.get())
            .isEqualTo(context);
        verifyImplicitContext(context);
        return true;
      });

      callable.call(); // runs assertion in the same scope
    }

    // Set another span between the time the task was made and executed.
    try (Scope scope2 = currentTraceContext.newScope(context2)) {
      callable.call(); // runs assertion
      verifyImplicitContext(context2);
    }
  }

  @Test public void restoresSpanAfterCallable() throws Exception {
    try (Scope scope0 = currentTraceContext.newScope(context)) {
      attachesSpanInCallable();
      assertThat(currentTraceContext.get())
          .isEqualTo(context);
      verifyImplicitContext(context);
    }
  }

  @Test public void attachesSpanInRunnable() throws Exception {
    Runnable runnable;
    try (Scope scope = currentTraceContext.newScope(context)) {
      runnable = currentTraceContext.wrap(() -> {
        assertThat(currentTraceContext.get())
            .isEqualTo(context);
        verifyImplicitContext(context);
      });

      runnable.run(); // runs assertion in the same scope
    }

    // Set another span between the time the task was made and executed.
    try (Scope scope2 = currentTraceContext.newScope(context2)) {
      runnable.run(); // runs assertion
      verifyImplicitContext(context2);
    }
  }

  @Test public void restoresSpanAfterRunnable() throws Exception {
    TraceContext context0 = TraceContext.newBuilder().traceId(3L).spanId(3L).build();

    try (Scope scope0 = currentTraceContext.newScope(context0)) {
      attachesSpanInRunnable();
      assertThat(currentTraceContext.get())
          .isEqualTo(context0);
      verifyImplicitContext(context0);
    }
  }

  @Test public void unloadable_unused() {
    assertRunIsUnloadableWithSupplier(Unused.class, currentSupplier());
  }

  static class Unused extends ClassLoaders.ConsumerRunnable<CurrentTraceContext> {
    @Override public void accept(CurrentTraceContext currentTraceContext) {
    }
  }

  @Test public void unloadable_afterScopeClose() {
    assertRunIsUnloadableWithSupplier(ClosedScope.class, currentSupplier());
  }

  static class ClosedScope extends ClassLoaders.ConsumerRunnable<CurrentTraceContext> {
    @Override public void accept(CurrentTraceContext current) {
      try (Scope ws = current.newScope(TraceContext.newBuilder().traceId(1L).spanId(2L).build())) {
      }
    }
  }

  /**
   * TODO: While it is an instrumentation bug to not close a scope, we should be tolerant. For
   * example, considering weak references or similar.
   */
  @SuppressWarnings("CheckReturnValue")
  @Test public void notUnloadable_whenScopeLeaked() {
    try {
      assertRunIsUnloadableWithSupplier(LeakedScope.class, currentSupplier());
      failBecauseExceptionWasNotThrown(AssertionError.class);
    } catch (AssertionError e) {
      // clear the leaked scope so other tests don't break
      currentTraceContext.newScope(null);
    }
  }

  static class LeakedScope extends ClassLoaders.ConsumerRunnable<CurrentTraceContext> {
    @Override public void accept(CurrentTraceContext current) {
      current.newScope(TraceContext.newBuilder().traceId(1L).spanId(2L).build());
    }
  }
}
